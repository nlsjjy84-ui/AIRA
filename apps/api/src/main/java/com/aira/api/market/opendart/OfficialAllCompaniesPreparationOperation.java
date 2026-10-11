package com.aira.api.market.opendart;

import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import java.util.Comparator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공식 회사 목록(OpenDART)에서 주식 종목코드가 있는 상장사를 차례로 골라, 아직 저장되지 않은 회사만
 * 연결 연간 재무를 가져와 저장한다. 한 번에 {@code limit}곳까지만 처리하고, 나머지는 다음 실행이 이어서 한다.
 *
 * <p>회사 하나가 실패해도 다음 회사로 넘어간다(회사 단위로 오류를 가둔다). 다만 한도 초과나 인증 오류이거나
 * 연속으로 5곳이 실패하면 호출을 낭비하지 않도록 그 자리에서 멈춘다. 연결재무제표가 없는 회사
 * (공시 없음, 필수 계정 없음)는 임의로 대체하지 않고 건너뛰며 그 수를 결과에 남긴다.
 */
@Service
public class OfficialAllCompaniesPreparationOperation {
    private static final Logger log = LoggerFactory.getLogger(OfficialAllCompaniesPreparationOperation.class);
    static final int MAX_CONSECUTIVE_FAILURES = 5;

    public enum StopReason { NONE, RATE_LIMIT, AUTHENTICATION, REPEATED_FAILURES, INTERRUPTED }

    public record Result(int pendingBefore, int attempted, int prepared, int noData, int failed,
            StopReason stopReason) {
        public int remaining() {
            return Math.max(0, pendingBefore - prepared - noData - failed);
        }

        public String summary() {
            return "OpenDART all-company refresh: pending=" + pendingBefore + ", attempted=" + attempted
                    + ", prepared=" + prepared + ", skippedNoConsolidatedData=" + noData
                    + ", failed=" + failed + ", remaining=" + remaining() + ", stop=" + stopReason;
        }
    }

    private final OfficialCompanyDataPreparationOperation companyData;
    private final EntityExternalIdentifierRegistryService identifiers;

    public OfficialAllCompaniesPreparationOperation(OfficialCompanyDataPreparationOperation companyData,
            EntityExternalIdentifierRegistryService identifiers) {
        this.companyData = companyData;
        this.identifiers = identifiers;
    }

    /**
     * @param skipStockCodes 이번 프로그램 실행 중 이미 "연결재무제표 없음/실패"로 확인한 종목코드. 처리하며 채워진다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result run(int businessYear, int limit, long pauseMillis, Set<String> skipStockCodes) {
        if (businessYear < 2000 || businessYear > 9999) {
            throw new IllegalArgumentException("Business year is invalid");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Batch limit must be positive");
        }
        var directory = companyData.fetchDirectory();
        var pending = directory.records().stream()
                .filter(record -> record.stockCode() != null)
                .sorted(Comparator.comparing(OpenDartCompanyDirectoryRecord::stockCode))
                .filter(record -> !skipStockCodes.contains(record.stockCode()))
                .filter(record -> identifiers.findEntity(
                        new ExternalIdentifierKey("OPENDART", "CORP_CODE", record.corpCode())).isEmpty())
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
                        log.warn("OpenDART company {} {} skipped: {}", record.stockCode(),
                                record.corpName(), e.category());
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                skipStockCodes.add(record.stockCode());
                consecutiveFailures++;
                log.warn("OpenDART company {} {} skipped: {}", record.stockCode(), record.corpName(),
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
