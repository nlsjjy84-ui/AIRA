package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudyControllerTests {
    @Test
    void notesAndTermsAreOwnedAndCounted() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:study-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        var c = new StudyController(jdbc);
        var me = new AiraPrincipal(UUID.randomUUID(), "me");
        var other = new AiraPrincipal(UUID.randomUUID(), "other");
        UUID company = UUID.randomUUID();
        assertEquals(400, c.save(me, new StudyController.NoteRequest(company, "삼성전자", "HACK", "t", "b", null)).getStatusCode().value());
        assertEquals(400, c.save(me, new StudyController.NoteRequest(company, "삼성전자", "EXPLAIN", " ", "b", null)).getStatusCode().value());
        var saved = c.save(me, new StudyController.NoteRequest(company, "삼성전자", "EXPLAIN", "영업이익률, 왜 올랐을까?", "매출보다 이익이 더 빨리 늘었어요.", "2025 사업보고서"));
        assertEquals(200, saved.getStatusCode().value());
        c.save(me, new StudyController.NoteRequest(null, null, "TERM", "PER", "시가총액 ÷ 순이익", null));
        c.learn(me, new StudyController.TermRequest("PER"));
        c.learn(me, new StudyController.TermRequest("PER"));
        c.learn(me, new StudyController.TermRequest("부채비율"));
        var mine = c.overview(me, null);
        assertEquals(Map.of("notes", 2, "terms", 2, "companies", 1), mine.get("stats"));
        assertEquals(1, ((List<?>) c.overview(me, company).get("notes")).size());
        assertEquals(Map.of("notes", 0, "terms", 0, "companies", 0), c.overview(other, null).get("stats"));
        UUID id = (UUID) saved.getBody().get("id");
        assertEquals(404, c.delete(other, id).getStatusCode().value());
        assertEquals(204, c.delete(me, id).getStatusCode().value());
    }
}
