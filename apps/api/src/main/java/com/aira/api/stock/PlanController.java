package com.aira.api.stock;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 사용량과 무료 한도. 요금 안내·내 공부 화면이 보여 준다. */
@RestController
public class PlanController {
    private final PlanLimits limits;
    private final JdbcTemplate jdbc;

    public PlanController(PlanLimits limits, JdbcTemplate jdbc) { this.limits = limits; this.jdbc = jdbc; }

    @GetMapping("/api/me/plan")
    public Map<String, Object> plan(@AuthenticationPrincipal AiraPrincipal principal) {
        Integer notes = jdbc.queryForObject("SELECT count(*) FROM user_study_note WHERE user_id=?", Integer.class, principal.userId());
        Integer alerts = jdbc.queryForObject("SELECT count(*) FROM user_price_alert WHERE user_id=?", Integer.class, principal.userId());
        return limits.usage(principal.userId(), notes == null ? 0 : notes, alerts == null ? 0 : alerts);
    }
}
