package com.aira.api.stock;

import com.aira.api.auth.security.AiraPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 종목 메모: 로그인한 사용자 본인만 읽고 쓴다. 빈 메모를 저장하면 지운다. */
@RestController
@RequestMapping("/api/me/stock-notes")
public class StockNoteController {
    static final int MAX = 4000;
    private final JdbcTemplate jdbc;

    public StockNoteController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record NoteRequest(String body) {}

    @GetMapping("/{companyId}")
    public Map<String, Object> read(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID companyId) {
        List<Map<String, Object>> rows = jdbc.query("SELECT body, updated_at FROM user_stock_note WHERE user_id=? AND company_id=?",
                (rs, i) -> note(rs.getString(1), rs.getTimestamp(2).toInstant()), principal.userId(), companyId);
        return rows.isEmpty() ? note("", null) : rows.get(0);
    }

    @PutMapping("/{companyId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> write(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID companyId,
            @RequestBody NoteRequest request) {
        String body = request == null || request.body() == null ? "" : request.body().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
        if (body.length() > MAX) return ResponseEntity.badRequest().build();
        jdbc.update("DELETE FROM user_stock_note WHERE user_id=? AND company_id=?", principal.userId(), companyId);
        if (body.isEmpty()) return ResponseEntity.ok(note("", null));
        Instant now = Instant.now();
        jdbc.update("INSERT INTO user_stock_note(user_id, company_id, body, updated_at) VALUES(?,?,?,?)",
                principal.userId(), companyId, body, Timestamp.from(now));
        return ResponseEntity.ok(note(body, now));
    }

    private static Map<String, Object> note(String body, Instant updatedAt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("body", body);
        out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return out;
    }
}
