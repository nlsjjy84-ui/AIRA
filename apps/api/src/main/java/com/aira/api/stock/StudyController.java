package com.aira.api.stock;

import com.aira.api.auth.security.AiraPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 공부: 저장한 설명(공부 노트)과 배운 용어. 본인만 읽고 쓴다. */
@RestController
@RequestMapping("/api/me/study")
public class StudyController {
    static final int MAX_NOTES = 500;
    static final Set<String> KINDS = Set.of("EXPLAIN", "TERM", "QA", "WHY", "DAY", "MEMO", "QUIZ", "INSPECT", "CHECK", "COMPARE", "COURSE");
    private final JdbcTemplate jdbc;

    private PlanLimits limits;

    public StudyController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLimits(PlanLimits limits) { this.limits = limits; }

    public record NoteRequest(UUID companyId, String companyName, String kind, String title, String body, String source) {}
    public record TermRequest(String term) {}

    @GetMapping
    public Map<String, Object> overview(@AuthenticationPrincipal AiraPrincipal principal, @RequestParam(required = false) UUID companyId) {
        UUID user = principal.userId();
        List<Map<String, Object>> notes = companyId == null
                ? jdbc.query("SELECT id, company_id, company_name, kind, title, body, source, created_at FROM user_study_note WHERE user_id=? ORDER BY created_at DESC", this::note, user)
                : jdbc.query("SELECT id, company_id, company_name, kind, title, body, source, created_at FROM user_study_note WHERE user_id=? AND company_id=? ORDER BY created_at DESC", this::note, user, companyId);
        List<Map<String, Object>> terms = jdbc.query("SELECT term, learned_at FROM user_learned_term WHERE user_id=? ORDER BY learned_at DESC",
                (rs, i) -> Map.of("term", rs.getString(1), "learnedAt", rs.getTimestamp(2).toInstant().toString()), user);
        Integer companies = jdbc.queryForObject("SELECT count(DISTINCT company_id) FROM user_study_note WHERE user_id=? AND company_id IS NOT NULL", Integer.class, user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("notes", notes);
        out.put("terms", terms);
        out.put("stats", Map.of("notes", notes.size(), "terms", terms.size(), "companies", companies == null ? 0 : companies));
        return out;
    }

    @PostMapping("/notes")
    public ResponseEntity<Map<String, Object>> save(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody NoteRequest request) {
        if (request == null || request.kind() == null || !KINDS.contains(request.kind())) return ResponseEntity.badRequest().build();
        String title = clean(request.title(), 300);
        String body = clean(request.body(), 6000);
        if (title.isEmpty() || body.isEmpty()) return ResponseEntity.badRequest().build();
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_study_note WHERE user_id=?", Integer.class, principal.userId());
        int max = limits == null ? MAX_NOTES : limits.notesLimit(principal.userId());
        if (count != null && count >= max) {
            return max < MAX_NOTES ? ResponseEntity.status(429).body(Map.of("code", "PLAN_LIMIT"))
                    : ResponseEntity.badRequest().body(Map.of("code", "TOO_MANY"));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_study_note(id,user_id,company_id,company_name,kind,title,body,source,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                id, principal.userId(), request.companyId(), clean(request.companyName(), 300), request.kind(), title, body,
                clean(request.source(), 500), Timestamp.from(Instant.now()));
        return ResponseEntity.ok(Map.of("id", id));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id) {
        return jdbc.update("DELETE FROM user_study_note WHERE user_id=? AND id=?", principal.userId(), id) > 0
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/terms")
    public ResponseEntity<Map<String, Object>> learn(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody TermRequest request) {
        String term = clean(request == null ? null : request.term(), 100);
        if (term.isEmpty()) return ResponseEntity.badRequest().build();
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM user_learned_term WHERE user_id=? AND term=?", Integer.class, principal.userId(), term);
        if (exists == null || exists == 0) {
            jdbc.update("INSERT INTO user_learned_term(user_id, term, learned_at) VALUES(?,?,?)", principal.userId(), term, Timestamp.from(Instant.now()));
        }
        return ResponseEntity.ok(Map.of("term", term));
    }

    private Map<String, Object> note(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject(1, UUID.class));
        m.put("companyId", rs.getObject(2, UUID.class));
        m.put("companyName", rs.getString(3));
        m.put("kind", rs.getString(4));
        m.put("title", rs.getString(5));
        m.put("body", rs.getString(6));
        m.put("source", rs.getString(7));
        m.put("createdAt", rs.getTimestamp(8).toInstant().toString());
        return m;
    }

    static String clean(String value, int max) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
