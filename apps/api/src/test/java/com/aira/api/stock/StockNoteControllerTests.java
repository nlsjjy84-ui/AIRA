package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StockNoteControllerTests {
    @Test
    void notesBelongToOneUserAndEmptyDeletes() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:notes-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        var controller = new StockNoteController(jdbc);
        var me = new AiraPrincipal(UUID.randomUUID(), "me");
        var other = new AiraPrincipal(UUID.randomUUID(), "other");
        UUID company = UUID.randomUUID();
        assertEquals("", controller.read(me, company).get("body"));
        var saved = controller.write(me, company, new StockNoteController.NoteRequest("  반기보고서 다시 읽기\n배당 확인  ")).getBody();
        assertEquals("반기보고서 다시 읽기\n배당 확인", saved.get("body"));
        assertNotNull(saved.get("updatedAt"));
        assertEquals("반기보고서 다시 읽기\n배당 확인", controller.read(me, company).get("body"));
        assertEquals("", controller.read(other, company).get("body"));
        controller.write(me, company, new StockNoteController.NoteRequest(" "));
        assertNull(controller.read(me, company).get("updatedAt"));
        assertEquals(400, controller.write(me, company, new StockNoteController.NoteRequest("가".repeat(4001))).getStatusCode().value());
    }
}
