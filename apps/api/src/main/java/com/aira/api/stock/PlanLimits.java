package com.aira.api.stock;

import com.aira.api.auth.service.DemoLoginService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 무료·AIRA 플러스 한도. 무료는 AI 설명 하루 5번, 공부 노트 30개, 가격 알림 3개.
 * 출시 전에는 꺼 두고(aira.plan.free-limits.enabled=false) 사용량만 센다. 켜도 공용 데모 계정에는 걸지 않는다.
 * 유료 결제는 아직 없으므로 모든 사용자를 무료로 본다.
 */
@Component
public class PlanLimits {
    public static final int FREE_AI_PER_DAY = 5;
    public static final int FREE_NOTES = 30;
    public static final int FREE_ALERTS = 3;
    static final int OPEN_NOTES = 500;
    static final int OPEN_ALERTS = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private record Day(LocalDate date, int count) {}

    private final boolean enforced;
    private final Clock clock;
    private final Map<UUID, Day> aiUse = new ConcurrentHashMap<>();

    @Autowired
    public PlanLimits(@Value("${aira.plan.free-limits.enabled:false}") boolean enforced) { this(enforced, Clock.systemUTC()); }

    PlanLimits(boolean enforced, Clock clock) { this.enforced = enforced; this.clock = clock; }

    public boolean enforced() { return enforced; }

    private boolean limited(UUID user) { return enforced && !DemoLoginService.isDemoUser(user); }

    /** AI 설명 한 번을 쓴다. 한도에 걸리면 false(세지 않음). */
    public boolean useAi(UUID user) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        boolean[] allowed = {true};
        aiUse.compute(user, (id, day) -> {
            int used = day == null || !day.date().equals(today) ? 0 : day.count();
            if (limited(user) && used >= FREE_AI_PER_DAY) { allowed[0] = false; return new Day(today, used); }
            return new Day(today, used + 1);
        });
        return allowed[0];
    }

    public int aiUsedToday(UUID user) {
        Day day = aiUse.get(user);
        return day == null || !day.date().equals(LocalDate.now(clock.withZone(KST))) ? 0 : day.count();
    }

    public int notesLimit(UUID user) { return limited(user) ? FREE_NOTES : OPEN_NOTES; }

    public int alertsLimit(UUID user) { return limited(user) ? FREE_ALERTS : OPEN_ALERTS; }

    public Map<String, Object> usage(UUID user, int notes, int alerts) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan", "FREE");
        out.put("enforced", enforced);
        out.put("demo", DemoLoginService.isDemoUser(user));
        out.put("ai", Map.of("used", aiUsedToday(user), "freeLimit", FREE_AI_PER_DAY));
        out.put("notes", Map.of("used", notes, "freeLimit", FREE_NOTES));
        out.put("alerts", Map.of("used", alerts, "freeLimit", FREE_ALERTS));
        return out;
    }

    /** 무료 한도를 넘었을 때. 화면은 code=PLAN_LIMIT을 보고 요금 안내로 이어 준다. */
    public static class LimitReached extends RuntimeException {
        public LimitReached() { super("PLAN_LIMIT"); }
    }
}
