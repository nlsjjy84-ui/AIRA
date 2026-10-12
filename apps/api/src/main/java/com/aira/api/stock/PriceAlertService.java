package com.aira.api.stock;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 종가 기준 가격 알림. 사용자가 정한 값일 뿐 AIRA가 가격을 제안하지 않는다. 한 사람당 50개까지. */
@Service
public class PriceAlertService {
    static final int MAX_PER_USER = 50;
    private PlanLimits limits;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLimits(PlanLimits limits) { this.limits = limits; }
    private final JdbcTemplate jdbc;
    private final StockCompanyLookup companies;
    private final StockPageService pages;

    public PriceAlertService(JdbcTemplate jdbc, StockCompanyLookup companies, StockPageService pages) {
        this.jdbc = jdbc; this.companies = companies; this.pages = pages;
    }

    public List<Map<String, Object>> list(UUID userId) {
        return jdbc.query("""
                SELECT id, company_id, company_name, short_code, direction, target, created_at, triggered_at, triggered_date, triggered_close
                FROM user_price_alert WHERE user_id=? ORDER BY triggered_at DESC NULLS FIRST, created_at DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getObject(1, UUID.class));
            m.put("companyId", rs.getObject(2, UUID.class));
            m.put("companyName", rs.getString(3));
            m.put("shortCode", rs.getString(4));
            m.put("direction", rs.getString(5));
            m.put("target", rs.getBigDecimal(6));
            m.put("createdAt", rs.getTimestamp(7).toInstant().toString());
            m.put("triggeredAt", rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant().toString());
            m.put("triggeredDate", rs.getObject(9, LocalDate.class) == null ? null : rs.getObject(9, LocalDate.class).toString());
            m.put("triggeredClose", rs.getBigDecimal(10));
            return m;
        }, userId);
    }

    /** 결과: null이면 성공, 아니면 거절 사유 코드. */
    public String create(UUID userId, UUID companyId, String direction, BigDecimal target) {
        if (!"ABOVE".equals(direction) && !"BELOW".equals(direction)) return "INVALID_DIRECTION";
        if (target == null || target.signum() <= 0 || target.compareTo(new BigDecimal("100000000")) > 0) return "INVALID_TARGET";
        var company = companies.find(companyId).orElse(null);
        if (company == null) return "UNKNOWN_COMPANY";
        String shortCode = pages.shortCode(company, pages.profile(company.corpCode()));
        if (shortCode == null) return "NO_SECURITY";
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_price_alert WHERE user_id=?", Integer.class, userId);
        int max = limits == null ? MAX_PER_USER : limits.alertsLimit(userId);
        if (count != null && count >= max) return max < MAX_PER_USER ? "PLAN_LIMIT" : "TOO_MANY";
        jdbc.update("""
                INSERT INTO user_price_alert(id,user_id,company_id,company_name,short_code,direction,target,created_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, companyId, company.name(), shortCode, direction, target, Timestamp.from(Instant.now()));
        return null;
    }

    public boolean delete(UUID userId, UUID id) {
        return jdbc.update("DELETE FROM user_price_alert WHERE user_id=? AND id=?", userId, id) > 0;
    }

    /** 새 종가가 들어온 뒤 부른다: 아직 울리지 않은 알림 중 조건에 닿은 것을 표시한다. 알림을 만든 뒤의 종가만 본다. */
    public int evaluate() {
        record Open(UUID id, String direction, BigDecimal target, LocalDate date, BigDecimal close) {}
        List<Open> open = jdbc.query("""
                SELECT a.id, a.direction, a.target, d.trading_date, d.close_price FROM user_price_alert a
                JOIN stock_price_daily d ON d.short_code = a.short_code
                 AND d.trading_date = (SELECT max(x.trading_date) FROM stock_price_daily x WHERE x.short_code = a.short_code)
                WHERE a.triggered_at IS NULL AND d.trading_date >= CAST(a.created_at AS DATE)
                """, (rs, i) -> new Open(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getObject(4, LocalDate.class), rs.getBigDecimal(5)));
        int hit = 0;
        for (Open o : open) {
            if (o.close() == null) continue;
            boolean reached = "ABOVE".equals(o.direction()) ? o.close().compareTo(o.target()) >= 0 : o.close().compareTo(o.target()) <= 0;
            if (!reached) continue;
            hit += jdbc.update("UPDATE user_price_alert SET triggered_at=?, triggered_date=?, triggered_close=? WHERE id=? AND triggered_at IS NULL",
                    Timestamp.from(Instant.now()), java.sql.Date.valueOf(o.date()), o.close(), o.id());
        }
        return hit;
    }
}
