package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.opendart.PriorYearBackfill.Result;
import com.aira.api.market.opendart.PriorYearBackfill.StopReason;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 저장된 회사에 2026-10 추가 항목(유동자산·유동부채·현금성자산·영업/투자/재무활동 현금흐름)을 채운다.
 *
 * <p>새 수집 경로를 만들지 않고, 회사를 처음 저장할 때 쓰는 같은 경로({@link OfficialCompanyDataPreparationOperation#prepareOne})를
 * 다시 부른다. 그래서 새 항목도 같은 공시의 접수번호·기간 확인 자료와 함께 저장된다. 이미 있는 항목은 같은 공시로 다시 확인될 뿐이다.
 * 그 해 매출이 있는데 추가 항목이 하나도 없는 회사만 고른다. 다시 불러도 추가 항목이 없는 회사(금융회사 등 표에 없는 경우)는
 * 이번 실행 동안 건너뛴다.</p>
 */
@Service
public class OfficialFactEnrichmentOperation {
    private static final Logger log = LoggerFactory.getLogger(OfficialFactEnrichmentOperation.class);
    static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final OfficialCompanyDataPreparationOperation companyData;
    private final JdbcTemplate jdbc;

    public OfficialFactEnrichmentOperation(OfficialCompanyDataPreparationOperation companyData, JdbcTemplate jdbc) {
        this.companyData = companyData;
        this.jdbc = jdbc;
    }

    static String extendedList() {
        return FactPredicate.EXTENDED_FINANCIALS.stream().map(p -> "'" + p.name() + "'").sorted()
                .collect(Collectors.joining(","));
    }

    /** 그 해 매출은 있지만 추가 항목이 하나도 없는 회사의 OpenDART 회사 코드. */
    Set<String> pendingCorpCodes(int businessYear) {
        LocalDate from = LocalDate.of(businessYear, 1, 1);
        LocalDate to = LocalDate.of(businessYear + 1, 1, 1);
        return new HashSet<>(jdbc.query("""
                SELECT i.identifier_value FROM entity_external_identifier i
                WHERE i.namespace = 'OPENDART' AND i.identifier_type = 'CORP_CODE'
                  AND EXISTS (SELECT 1 FROM fact f WHERE f.subject_entity_id = i.entity_id AND f.predicate = 'REVENUE'
                              AND f.status = 'SUPPORTED' AND f.period_start >= ? AND f.period_start < ?)
                  AND NOT EXISTS (SELECT 1 FROM fact f WHERE f.subject_entity_id = i.entity_id
                              AND f.predicate IN (""" + extendedList() + """
                              ) AND f.status = 'SUPPORTED' AND f.period_start >= ? AND f.period_start < ?)
                """, (rs, n) -> rs.getString(1), from, to, from, to));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result run(int businessYear, int limit, long pauseMillis, Set<String> skipStockCodes) {
        if (businessYear < 2000 || businessYear > 9999) throw new IllegalArgumentException("Business year is invalid");
        if (limit < 1) throw new IllegalArgumentException("Batch limit must be positive");
        Set<String> corpCodes = pendingCorpCodes(businessYear);
        if (corpCodes.isEmpty()) return new Result(0, 0, 0, 0, 0, StopReason.NONE);
        var directory = companyData.fetchDirectory();
        var pending = directory.records().stream()
                .filter(record -> record.stockCode() != null && corpCodes.contains(record.corpCode()))
                .filter(record -> !skipStockCodes.contains(record.stockCode()))
                .sorted(Comparator.comparing(OpenDartCompanyDirectoryRecord::stockCode))
                .toList();

        int attempted = 0, prepared = 0, noData = 0, failed = 0, consecutiveFailures = 0;
        var stop = StopReason.NONE;
        for (var record : pending) {
            if (attempted >= limit) break;
            attempted++;
            try {
                companyData.prepareOne(directory, record.stockCode(), businessYear);
                // 공시에 추가 항목이 아예 없는 회사는 다음 묶음에서 다시 부르지 않는다.
                if (pendingCorpCodes(businessYear).contains(record.corpCode())) {
                    noData++;
                    skipStockCodes.add(record.stockCode());
                } else {
                    prepared++;
                }
                consecutiveFailures = 0;
            } catch (OpenDartProviderException e) {
                switch (e.category()) {
                    case NO_DATA -> { noData++; skipStockCodes.add(record.stockCode()); consecutiveFailures = 0; }
                    case RATE_LIMIT -> stop = StopReason.RATE_LIMIT;
                    case AUTHENTICATION -> stop = StopReason.AUTHENTICATION;
                    default -> {
                        failed++;
                        skipStockCodes.add(record.stockCode());
                        consecutiveFailures++;
                        log.warn("OpenDART enrichment {} {} {} skipped: {}", businessYear, record.stockCode(),
                                record.corpName(), e.category());
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                skipStockCodes.add(record.stockCode());
                consecutiveFailures++;
                log.warn("OpenDART enrichment {} {} {} skipped: {}", businessYear, record.stockCode(), record.corpName(),
                        e.getClass().getSimpleName());
            }
            if (stop == StopReason.NONE && consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) stop = StopReason.REPEATED_FAILURES;
            if (stop != StopReason.NONE) break;
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
