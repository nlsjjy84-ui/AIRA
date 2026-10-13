package com.aira.api.stock;

import com.aira.api.auth.security.AiraPrincipal;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공부 반: 강사(반장)가 반을 만들고 초대 코드로 수강생을 받는다. 반장은 "같은 회사 같이 읽기" 과제를 내고,
 * 수강생이 회사 읽기 코스(10단계)를 몇 단계까지 했는지 한 화면에서 본다. 수강생은 자기 진도만 본다.
 * 진도는 수강생이 이미 남기는 공부 노트(kind=COURSE)에서 세므로 따로 제출할 것이 없다.
 */
@RestController
@RequestMapping("/api/me/classes")
public class StudyClassController {
    static final int MAX_OWNED = 5;
    static final int MAX_MEMBERS = 300;
    static final int MAX_ASSIGNMENTS = 50;
    static final int COURSE_STEPS = 10;
    /** 이 기간 안에 나온 과제는 수강생 화면에 「새 과제」로 표시한다. */
    static final int NEW_DAYS = 7;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern COURSE_STEP = Pattern.compile("^회사 읽기 코스 (\\d{1,2})\\. ");
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public StudyClassController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record CreateRequest(String name) {}
    public record JoinRequest(String code) {}
    public record AssignmentRequest(UUID companyId, String companyName, String title, String dueDate) {}

    /** 내가 만든 반과 들어간 반. */
    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AiraPrincipal principal) {
        UUID me = principal.userId();
        List<Map<String, Object>> owned = jdbc.query("""
                SELECT c.id, c.name, c.invite_code, c.sample,
                  (SELECT count(*) FROM study_class_member m WHERE m.class_id=c.id),
                  (SELECT count(*) FROM study_class_assignment a WHERE a.class_id=c.id)
                FROM study_class c WHERE c.owner_user_id=? ORDER BY c.created_at""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getObject(1, UUID.class));
            m.put("name", rs.getString(2));
            m.put("inviteCode", rs.getString(3));
            m.put("sample", rs.getBoolean(4));
            m.put("members", rs.getInt(5));
            m.put("assignments", rs.getInt(6));
            return m;
        }, me);
        List<Map<String, Object>> joined = jdbc.query("""
                SELECT c.id, c.name, (SELECT count(*) FROM study_class_assignment a WHERE a.class_id=c.id),
                  (SELECT count(*) FROM study_class_assignment a WHERE a.class_id=c.id AND a.created_at >= ?)
                FROM study_class c JOIN study_class_member m ON m.class_id=c.id WHERE m.user_id=? ORDER BY m.joined_at""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getObject(1, UUID.class));
            m.put("name", rs.getString(2));
            m.put("assignments", rs.getInt(3));
            m.put("newAssignments", rs.getInt(4));
            return m;
        }, Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(NEW_DAYS))), me);
        return Map.of("owned", owned, "joined", joined);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody CreateRequest request) {
        String name = StudyController.clean(request == null ? null : request.name(), 100);
        if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("code", "NAME_REQUIRED"));
        Integer owned = jdbc.queryForObject("SELECT count(*) FROM study_class WHERE owner_user_id=?", Integer.class, principal.userId());
        if (owned != null && owned >= MAX_OWNED) return ResponseEntity.badRequest().body(Map.of("code", "TOO_MANY"));
        UUID id = UUID.randomUUID();
        String code = newCode();
        jdbc.update("INSERT INTO study_class(id, owner_user_id, name, invite_code, sample, created_at) VALUES(?,?,?,?,?,?)",
                id, principal.userId(), name, code, false, Timestamp.from(Instant.now()));
        return ResponseEntity.ok(Map.of("id", id, "inviteCode", code));
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> join(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody JoinRequest request) {
        String code = request == null || request.code() == null ? "" : request.code().strip().toUpperCase();
        if (!code.matches("[A-Z0-9]{6,12}")) return ResponseEntity.badRequest().body(Map.of("code", "BAD_CODE"));
        List<Map<String, Object>> found = jdbc.query("SELECT id, owner_user_id, name FROM study_class WHERE invite_code=?",
                (rs, i) -> Map.of("id", rs.getObject(1, UUID.class), "owner", rs.getObject(2, UUID.class), "name", rs.getString(3)), code);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND"));
        UUID classId = (UUID) found.get(0).get("id");
        if (principal.userId().equals(found.get(0).get("owner"))) return ResponseEntity.badRequest().body(Map.of("code", "OWNER"));
        Integer members = jdbc.queryForObject("SELECT count(*) FROM study_class_member WHERE class_id=?", Integer.class, classId);
        Integer already = jdbc.queryForObject("SELECT count(*) FROM study_class_member WHERE class_id=? AND user_id=?", Integer.class, classId, principal.userId());
        if (already == null || already == 0) {
            if (members != null && members >= MAX_MEMBERS) return ResponseEntity.badRequest().body(Map.of("code", "FULL"));
            jdbc.update("INSERT INTO study_class_member(class_id, user_id, display_name, joined_at) VALUES(?,?,?,?)",
                    classId, principal.userId(), StudyController.clean(principal.nickname(), 40), Timestamp.from(Instant.now()));
        }
        return ResponseEntity.ok(Map.of("id", classId, "name", found.get(0).get("name")));
    }

    /** 반장은 전체 진도표를, 수강생은 과제와 자기 진도만 본다. 둘 다 아니면 404. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id) {
        UUID me = principal.userId();
        List<Map<String, Object>> found = jdbc.query("SELECT owner_user_id, name, invite_code, sample FROM study_class WHERE id=?",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("owner", rs.getObject(1, UUID.class));
                    m.put("name", rs.getString(2));
                    m.put("code", rs.getString(3));
                    m.put("sample", rs.getBoolean(4));
                    return m;
                }, id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        boolean owner = me.equals(found.get(0).get("owner"));
        Integer member = jdbc.queryForObject("SELECT count(*) FROM study_class_member WHERE class_id=? AND user_id=?", Integer.class, id, me);
        if (!owner && (member == null || member == 0)) return ResponseEntity.notFound().build();

        List<Map<String, Object>> assignments = jdbc.query(
                "SELECT id, company_id, company_name, title, due_date, created_at FROM study_class_assignment WHERE class_id=? ORDER BY created_at", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getObject(1, UUID.class));
                    m.put("companyId", rs.getObject(2, UUID.class));
                    m.put("companyName", rs.getString(3));
                    m.put("title", rs.getString(4));
                    Date due = rs.getDate(5);
                    m.put("dueDate", due == null ? null : due.toLocalDate().toString());
                    m.put("isNew", rs.getTimestamp(6).toInstant().isAfter(Instant.now().minus(java.time.Duration.ofDays(NEW_DAYS))));
                    return m;
                }, id);
        List<UUID> companies = assignments.stream().map(a -> (UUID) a.get("companyId")).distinct().toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", found.get(0).get("name"));
        out.put("role", owner ? "OWNER" : "MEMBER");
        out.put("sample", found.get(0).get("sample"));
        out.put("courseSteps", COURSE_STEPS);
        out.put("assignments", assignments);
        if (owner) {
            out.put("inviteCode", found.get(0).get("code"));
            List<Map<String, Object>> members = jdbc.query(
                    "SELECT user_id, display_name FROM study_class_member WHERE class_id=? ORDER BY joined_at", (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("userId", rs.getObject(1, UUID.class));
                        m.put("name", rs.getString(2));
                        return m;
                    }, id);
            Map<UUID, Map<UUID, Set<Integer>>> progress = progress(members.stream().map(m -> (UUID) m.get("userId")).toList(), companies);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> m : members) {
                Map<UUID, Set<Integer>> byCompany = progress.getOrDefault((UUID) m.get("userId"), Map.of());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.get("userId"));
                row.put("name", m.get("name"));
                row.put("done", assignments.stream().map(a -> byCompany.getOrDefault((UUID) a.get("companyId"), Set.of()).size()).toList());
                rows.add(row);
            }
            out.put("members", rows);
        } else {
            Map<UUID, Set<Integer>> mine = progress(List.of(me), companies).getOrDefault(me, Map.of());
            out.put("myDone", assignments.stream().map(a -> mine.getOrDefault((UUID) a.get("companyId"), Set.of()).size()).toList());
        }
        return ResponseEntity.ok(out);
    }

    /**
     * 반장이 한 수강생의 과제(회사) 코스 노트를 읽는다. 수강생은 반에 들어올 때 이 사실을 안내받는다.
     * 반장이 아니거나, 반 수강생이 아니거나, 이 반의 과제가 아니면 404.
     */
    @GetMapping("/{id}/notes")
    public ResponseEntity<Map<String, Object>> memberNotes(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam UUID member, @org.springframework.web.bind.annotation.RequestParam UUID assignment) {
        if (!owns(principal.userId(), id)) return ResponseEntity.notFound().build();
        List<String> names = jdbc.query("SELECT display_name FROM study_class_member WHERE class_id=? AND user_id=?", (rs, i) -> rs.getString(1), id, member);
        List<Map<String, Object>> task = jdbc.query("SELECT company_id, company_name FROM study_class_assignment WHERE id=? AND class_id=?",
                (rs, i) -> Map.of("companyId", rs.getObject(1, UUID.class), "companyName", rs.getString(2)), assignment, id);
        if (names.isEmpty() || task.isEmpty()) return ResponseEntity.notFound().build();
        List<Map<String, Object>> notes = jdbc.query("""
                SELECT title, body, created_at FROM user_study_note
                WHERE user_id=? AND company_id=? AND kind='COURSE' ORDER BY created_at""", (rs, i) -> {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("title", rs.getString(1));
            n.put("body", rs.getString(2));
            n.put("createdAt", rs.getTimestamp(3).toInstant().toString());
            return n;
        }, member, task.get(0).get("companyId"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("member", names.get(0));
        out.put("companyName", task.get(0).get("companyName"));
        out.put("notes", notes);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<Map<String, Object>> assign(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id,
            @RequestBody AssignmentRequest request) {
        if (!owns(principal.userId(), id)) return ResponseEntity.notFound().build();
        if (request == null || request.companyId() == null) return ResponseEntity.badRequest().body(Map.of("code", "COMPANY_REQUIRED"));
        String companyName = StudyController.clean(request.companyName(), 300);
        if (companyName.isEmpty()) return ResponseEntity.badRequest().body(Map.of("code", "COMPANY_REQUIRED"));
        String title = StudyController.clean(request.title(), 200);
        if (title.isEmpty()) title = companyName + " 회사 읽기 코스";
        LocalDate due = null;
        if (request.dueDate() != null && !request.dueDate().isBlank()) {
            try { due = LocalDate.parse(request.dueDate().strip()); }
            catch (RuntimeException bad) { return ResponseEntity.badRequest().body(Map.of("code", "BAD_DATE")); }
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM study_class_assignment WHERE class_id=?", Integer.class, id);
        if (count != null && count >= MAX_ASSIGNMENTS) return ResponseEntity.badRequest().body(Map.of("code", "TOO_MANY"));
        UUID assignment = UUID.randomUUID();
        jdbc.update("INSERT INTO study_class_assignment(id, class_id, company_id, company_name, title, due_date, created_at) VALUES(?,?,?,?,?,?,?)",
                assignment, id, request.companyId(), companyName, title, due == null ? null : Date.valueOf(due), Timestamp.from(Instant.now()));
        return ResponseEntity.ok(Map.of("id", assignment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id) {
        if (!owns(principal.userId(), id)) return ResponseEntity.notFound().build();
        jdbc.update("DELETE FROM study_class_assignment WHERE class_id=?", id);
        jdbc.update("DELETE FROM study_class_member WHERE class_id=?", id);
        jdbc.update("DELETE FROM study_class WHERE id=?", id);
        return ResponseEntity.noContent().build();
    }

    /** 수강생이 반에서 나간다. 자기 공부 노트는 그대로 남는다. */
    @DeleteMapping("/{id}/membership")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id) {
        return jdbc.update("DELETE FROM study_class_member WHERE class_id=? AND user_id=?", id, principal.userId()) > 0
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private boolean owns(UUID user, UUID classId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM study_class WHERE id=? AND owner_user_id=?", Integer.class, classId, user);
        return n != null && n > 0;
    }

    /** 사용자 → 회사 → 끝낸 코스 단계 번호. 코스 노트 제목("회사 읽기 코스 3. …")에서 단계를 읽는다. */
    Map<UUID, Map<UUID, Set<Integer>>> progress(List<UUID> users, List<UUID> companies) {
        Map<UUID, Map<UUID, Set<Integer>>> out = new HashMap<>();
        if (users.isEmpty() || companies.isEmpty()) return out;
        String userMarks = String.join(",", users.stream().map(u -> "?").toList());
        String companyMarks = String.join(",", companies.stream().map(c -> "?").toList());
        List<Object> args = new ArrayList<>(users);
        args.addAll(companies);
        jdbc.query("SELECT user_id, company_id, title FROM user_study_note WHERE kind='COURSE' AND user_id IN (" + userMarks
                + ") AND company_id IN (" + companyMarks + ")", rs -> {
            Matcher m = COURSE_STEP.matcher(rs.getString(3));
            if (!m.find()) return;
            int step = Integer.parseInt(m.group(1));
            if (step < 1 || step > COURSE_STEPS) return;
            out.computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>())
                    .computeIfAbsent(rs.getObject(2, UUID.class), k -> new HashSet<>()).add(step);
        }, args.toArray());
        return out;
    }

    String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            Integer used = jdbc.queryForObject("SELECT count(*) FROM study_class WHERE invite_code=?", Integer.class, code.toString());
            if (used == null || used == 0) return code.toString();
        }
        throw new IllegalStateException("Could not make an invite code");
    }

    /**
     * 면접관용 데모: 데모 계정이 반장인 예시 반 하나와 예시 수강생 3명의 진도를 만든다(로그인할 수 없는 예시 데이터, 화면에 「예시」로 표시).
     * 이미 있으면 아무것도 하지 않는다.
     */
    public void seedSample(UUID demoUser, UUID companyId, String companyName) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM study_class WHERE owner_user_id=? AND sample=TRUE", Integer.class, demoUser);
        if (exists != null && exists > 0) return;
        UUID classId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO study_class(id, owner_user_id, name, invite_code, sample, created_at) VALUES(?,?,?,?,?,?)",
                classId, demoUser, "예시 반 · 주식 첫걸음 3기", newCode(), true, Timestamp.from(now));
        jdbc.update("INSERT INTO study_class_assignment(id, class_id, company_id, company_name, title, due_date, created_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID(), classId, companyId, companyName, companyName + " 회사 읽기 코스 끝내기", Date.valueOf(LocalDate.now().plusDays(7)), Timestamp.from(now));
        int[][] done = { { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, { 1, 2, 3, 4 }, { 1 } };
        String[] names = { "예시 수강생 A", "예시 수강생 B", "예시 수강생 C" };
        for (int i = 0; i < names.length; i++) {
            UUID member = UUID.randomUUID();
            jdbc.update("INSERT INTO study_class_member(class_id, user_id, display_name, joined_at) VALUES(?,?,?,?)",
                    classId, member, names[i], Timestamp.from(now));
            for (int step : done[i]) {
                jdbc.update("INSERT INTO user_study_note(id,user_id,company_id,company_name,kind,title,body,source,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), member, companyId, companyName, "COURSE", "회사 읽기 코스 " + step + ". 예시 단계",
                        "예시 수강생이 남긴 한 줄 노트(데모용 예시 데이터)", "예시", Timestamp.from(now));
            }
        }
    }
}
