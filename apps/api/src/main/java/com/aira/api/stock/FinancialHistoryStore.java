package com.aira.api.stock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FinancialHistoryStore {
    private final JdbcTemplate jdbc;

    public FinancialHistoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Set<String> loadedChunks(int reportYear) {
        return Set.copyOf(jdbc.query("SELECT chunk_start FROM company_financial_history_loaded WHERE report_year=?",
                (rs, i) -> rs.getString(1), reportYear));
    }

    @Transactional
    public void saveChunk(int reportYear, String chunkStart, List<FinancialHistoryRows.Value> values) {
        for (FinancialHistoryRows.Value v : values) {
            // 같은 (회사, 사업연도, 항목)은 더 최근 보고서 값을 남긴다(정정·재작성 반영).
            jdbc.update("DELETE FROM company_financial_history WHERE corp_code=? AND fiscal_year=? AND account=? AND report_year<=?",
                    v.corpCode(), v.fiscalYear(), v.account(), v.reportYear());
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM company_financial_history WHERE corp_code=? AND fiscal_year=? AND account=?",
                    Integer.class, v.corpCode(), v.fiscalYear(), v.account());
            if (exists != null && exists > 0) continue;
            jdbc.update("""
                    INSERT INTO company_financial_history(corp_code,fiscal_year,account,amount,fs_div,receipt_no,report_year)
                    VALUES(?,?,?,?,?,?,?)
                    """, v.corpCode(), v.fiscalYear(), v.account(), v.amount(), v.fsDiv(), v.receiptNo(), v.reportYear());
        }
        jdbc.update("DELETE FROM company_financial_history_loaded WHERE report_year=? AND chunk_start=?", reportYear, chunkStart);
        jdbc.update("INSERT INTO company_financial_history_loaded(report_year,chunk_start,row_count,loaded_at) VALUES(?,?,?,?)",
                reportYear, chunkStart, values.size(), Timestamp.from(Instant.now()));
    }

    public List<FinancialHistoryRows.Value> forCompany(String corpCode) {
        return jdbc.query("""
                SELECT corp_code,fiscal_year,account,amount,fs_div,receipt_no,report_year
                FROM company_financial_history WHERE corp_code=? ORDER BY fiscal_year, account
                """, (rs, i) -> new FinancialHistoryRows.Value(rs.getString(1), rs.getInt(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getInt(7)), corpCode);
    }
}
