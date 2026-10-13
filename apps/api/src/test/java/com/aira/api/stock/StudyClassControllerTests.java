package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudyClassControllerTests {
    private static JdbcTemplate db() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:class-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        return jdbc;
    }

    @Test
    void teacherSeesEveryStudentsCourseProgressAndStudentsSeeOnlyTheirOwn() {
        var jdbc = db();
        var classes = new StudyClassController(jdbc);
        var study = new StudyController(jdbc);
        var teacher = new AiraPrincipal(UUID.randomUUID(), "강사");
        var kim = new AiraPrincipal(UUID.randomUUID(), "김수강");
        var lee = new AiraPrincipal(UUID.randomUUID(), "이수강");
        var stranger = new AiraPrincipal(UUID.randomUUID(), "남");
        UUID samsung = UUID.randomUUID();

        assertEquals(400, classes.create(teacher, new StudyClassController.CreateRequest(" ")).getStatusCode().value());
        var created = classes.create(teacher, new StudyClassController.CreateRequest("주식 첫걸음 1기")).getBody();
        UUID classId = (UUID) created.get("id");
        String code = (String) created.get("inviteCode");
        assertTrue(code.matches("[A-Z2-9]{8}"));

        assertEquals(400, classes.join(teacher, new StudyClassController.JoinRequest(code)).getStatusCode().value(), "owner cannot join own class");
        assertEquals(404, classes.join(kim, new StudyClassController.JoinRequest("ZZZZZZZZ")).getStatusCode().value());
        assertEquals(200, classes.join(kim, new StudyClassController.JoinRequest(code.toLowerCase())).getStatusCode().value());
        assertEquals(200, classes.join(kim, new StudyClassController.JoinRequest(code)).getStatusCode().value(), "joining twice is harmless");
        classes.join(lee, new StudyClassController.JoinRequest(code));

        assertEquals(404, classes.assign(kim, classId, new StudyClassController.AssignmentRequest(samsung, "삼성전자", null, null)).getStatusCode().value(),
                "only the owner gives assignments");
        assertEquals(400, classes.assign(teacher, classId, new StudyClassController.AssignmentRequest(samsung, "삼성전자", null, "next week")).getStatusCode().value());
        assertEquals(200, classes.assign(teacher, classId, new StudyClassController.AssignmentRequest(samsung, "삼성전자", null, "2026-10-20")).getStatusCode().value());

        // 김수강은 코스 3단계(같은 단계 두 번 포함), 다른 회사 노트는 세지 않는다.
        for (String title : List.of("회사 읽기 코스 1. 어떤 회사인가", "회사 읽기 코스 2. 얼마나 파나", "회사 읽기 코스 2. 얼마나 파나", "회사 읽기 코스 3. 본업으로 얼마 남기나")) {
            study.save(kim, new StudyController.NoteRequest(samsung, "삼성전자", "COURSE", title, "한 줄", null));
        }
        study.save(kim, new StudyController.NoteRequest(UUID.randomUUID(), "다른회사", "COURSE", "회사 읽기 코스 4. 빚", "x", null));
        study.save(kim, new StudyController.NoteRequest(samsung, "삼성전자", "MEMO", "회사 읽기 코스 5. 메모", "x", null));

        Map<String, Object> board = classes.detail(teacher, classId).getBody();
        assertEquals("OWNER", board.get("role"));
        assertEquals(code, board.get("inviteCode"));
        assertEquals("삼성전자 회사 읽기 코스", ((Map<?, ?>) ((List<?>) board.get("assignments")).get(0)).get("title"));
        assertEquals("2026-10-20", ((Map<?, ?>) ((List<?>) board.get("assignments")).get(0)).get("dueDate"));
        List<?> members = (List<?>) board.get("members");
        assertEquals(Map.of("id", kim.userId(), "name", "김수강", "done", List.of(3)), members.get(0));
        assertEquals(Map.of("id", lee.userId(), "name", "이수강", "done", List.of(0)), members.get(1));
        UUID assignmentId = (UUID) ((Map<?, ?>) ((List<?>) board.get("assignments")).get(0)).get("id");
        assertEquals(true, ((Map<?, ?>) ((List<?>) board.get("assignments")).get(0)).get("isNew"));
        var notes = classes.memberNotes(teacher, classId, kim.userId(), assignmentId);
        assertEquals(4, ((List<?>) notes.getBody().get("notes")).size(), "only this company's course notes, in order");
        assertEquals("회사 읽기 코스 1. 어떤 회사인가", ((Map<?, ?>) ((List<?>) notes.getBody().get("notes")).get(0)).get("title"));
        assertEquals(404, classes.memberNotes(kim, classId, lee.userId(), assignmentId).getStatusCode().value(), "students cannot read each other");
        assertEquals(404, classes.memberNotes(teacher, classId, stranger.userId(), assignmentId).getStatusCode().value());

        Map<String, Object> mine = classes.detail(kim, classId).getBody();
        assertEquals("MEMBER", mine.get("role"));
        assertEquals(List.of(3), mine.get("myDone"));
        assertNull(mine.get("members"), "students never see other students");
        assertNull(mine.get("inviteCode"));
        assertEquals(404, classes.detail(stranger, classId).getStatusCode().value());

        assertEquals(1, ((List<?>) classes.list(teacher).get("owned")).size());
        assertEquals(1, ((List<?>) classes.list(kim).get("joined")).size());
        assertEquals(1, ((Map<?, ?>) ((List<?>) classes.list(kim).get("joined")).get(0)).get("newAssignments"));
        assertEquals(204, classes.leave(lee, classId).getStatusCode().value());
        assertEquals(404, classes.remove(kim, classId).getStatusCode().value());
        assertEquals(204, classes.remove(teacher, classId).getStatusCode().value());
        assertTrue(((List<?>) classes.list(kim).get("joined")).isEmpty());
    }

    @Test
    void ownerHasAClassLimitAndTheDemoSampleIsCreatedOnce() {
        var jdbc = db();
        var classes = new StudyClassController(jdbc);
        var teacher = new AiraPrincipal(UUID.randomUUID(), "강사");
        for (int i = 0; i < StudyClassController.MAX_OWNED; i++) classes.create(teacher, new StudyClassController.CreateRequest("반 " + i));
        assertEquals(400, classes.create(teacher, new StudyClassController.CreateRequest("하나 더")).getStatusCode().value());

        UUID demo = UUID.randomUUID();
        UUID samsung = UUID.randomUUID();
        classes.seedSample(demo, samsung, "삼성전자");
        classes.seedSample(demo, samsung, "삼성전자");
        List<?> owned = (List<?>) classes.list(new AiraPrincipal(demo, "데모계정")).get("owned");
        assertEquals(1, owned.size());
        Map<?, ?> sample = (Map<?, ?>) owned.get(0);
        assertEquals(true, sample.get("sample"));
        Map<String, Object> board = classes.detail(new AiraPrincipal(demo, "데모계정"), (UUID) sample.get("id")).getBody();
        List<?> rows = (List<?>) board.get("members");
        assertEquals(3, rows.size());
        assertEquals(List.of(10), ((Map<?, ?>) rows.get(0)).get("done"));
        assertEquals(List.of(4), ((Map<?, ?>) rows.get(1)).get("done"));
        assertFalse(((String) ((Map<?, ?>) rows.get(2)).get("name")).isBlank());
    }
}
