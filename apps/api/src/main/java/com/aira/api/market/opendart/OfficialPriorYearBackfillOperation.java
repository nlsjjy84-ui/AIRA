package com.aira.api.market.opendart;

import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 저장된 상장사에 대해 이전 사업연도의 연결 연간 재무를 채운다. 새 방식을 만들지 않고, 최근 연도를 저장할 때 쓰는
 * 같은 경로({@link OfficialCompanyDataPreparationOperation#prepareOne})를 연도만 바꿔 다시 쓴다. 그래서 연도마다
 * 그 해 공시의 접수번호·기간 확인 자료가 따로 저장된다(한 공시의 전기 값을 빌려 쓰지 않는다).
 * 해당 연도 값이 이미 있는 회사는 건너뛰어 다시 호출하지 않는다.
 */
@Service
public class OfficialPriorYearBackfillOperation implements PriorYearBackfill {
    private static final Logger log = LoggerFactory.getLogger(OfficialPriorYearBackfillOperation.class);
    static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final OfficialCompanyDataPreparationOperation companyData;
    private final EntityExternalIdentifierRegistryService identifiers;
    private final JdbcTemplate jdbc;

    public OfficialPriorYearBackfillOperation(OfficialCompanyDataPreparationOperation companyData,
            EntityExternalIdentifierRegistryService identifiers, JdbcTemplate jdbc) {
        this.companyData = companyData;
        this.identifiers = identifiers;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result run(int businessYear, int limit, long pauseMillis, Set<String> skipStockCodes) {
        if (businessYear < 2000 || businessYear > 9999) {
            throw new IllegalArgumentException("Business year is invalid");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Batch limit must be positive");
        }
        Set<UUID> alreadyHave = new HashSet<>(jdbc.query("""
                SELECT DISTINCT subject_entity_id FROM fact
                WHERE predicate IN ('REVENUE', 'TOTAL_ASSETS') AND status = 'SUPPORTED'
                  AND period_start >= ? AND period_start < ?
                """, (rs, n) -> rs.getObject(1, UUID.class),
                LocalDate.of(businessYear, 1, 1), LocalDate.of(businessYear + 1, 1, 1)));
        var directory = companyData.fetchDirectory();
        var pending = directory.records().stream()
                .filter(record -> record.stockCode() != null)
                .sorted(Comparator.comparing(OpenDartCompanyDirectoryRecord::stockCode))
                .filter(record -> !skipStockCodes.contains(record.stockCode()))
                .filter(record -> {
                    var entity = identifiers.findEntity(new ExternalIdentifierKey("OPENDART", "CORP_CODE", record.corpCode()));
                    return entity.isPresent() && !alreadyHave.contains(entity.get().getId());
                })
                .toList();

        int attempted = 0;
        int prepared = 0;
        int noData = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        var stop = StopReason.NONE;
        for (var record : pending) {
            if (attempted >= limit) {
                break;
            }
            attempted++;
            try {
                companyData.prepareOne(directory, record.stockCode(), businessYear);
                prepared++;
                consecutiveFailures = 0;
            } catch (OpenDartProviderException e) {
                switch (e.category()) {
                    case NO_DATA -> {
                        noData++;
                        skipStockCodes.add(record.stockCode());
                        consecutiveFailures = 0;
                    }
                    case RATE_LIMIT -> stop = StopReason.RATE_LIMIT;
                    case AUTHENTICATION -> stop = StopReason.AUTHENTICATION;
                    default -> {
                        failed++;
                        skipStockCodes.add(record.stockCode());
                        consecutiveFailures++;
                        log.warn("OpenDART backfill {} {} {} skipped: {}", businessYear, record.stockCode(),
                                record.corpName(), e.category());
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                skipStockCodes.add(record.stockCode());
                consecutiveFailures++;
                log.warn("OpenDART backfill {} {} {} skipped: {}", businessYear, record.stockCode(), record.corpName(),
                        e.getClass().getSimpleName());
            }
            if (stop == StopReason.NONE && consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                stop = StopReason.REPEATED_FAILURES;
            }
            if (stop != StopReason.NONE) {
                break;
            }
            if (pauseMillis > 0) {
                try {
                    Thread.sleep(pauseMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stop = StopReason.INTERRUPTED;
                    break;
                }
            }
        }
        return new Result(pending.size(), attempted, prepared, noData, failed, stop);
    }
}
