package com.aira.api.auth.security;

import com.aira.api.auth.exception.LoginTemporarilyLockedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 시도 제한 — 같은 닉네임으로 15분 안에 5번 틀리면 15분 동안 로그인을 막는다(무차별 대입 방어).
 *
 * <p>정규화한 닉네임 기준으로 세기 때문에, 없는 닉네임도 똑같이 막혀 계정이 있는지 없는지 드러나지 않는다.
 * 성공하면 그 닉네임의 실패 기록을 지운다. 기록은 서버 메모리에만 두고(개인정보 저장 없음), 키가 많아지면 오래된 것부터 정리한다.
 */
public class LoginAttemptLimiter {
    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public static LoginAttemptLimiter systemDefault() {
        return new LoginAttemptLimiter(Clock.systemUTC());
    }

    /** 막힌 상태면 LoginTemporarilyLockedException(남은 초)을 던진다. 비밀번호를 확인하기 전에 부른다. */
    public void checkAllowed(String key) {
        if (key == null) return;
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) return;
        Instant now = clock.instant();
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() >= MAX_FAILURES) {
                Instant unlockAt = attempts.peekFirst().plus(WINDOW);
                long seconds = Math.max(1, Duration.between(now, unlockAt).toSeconds());
                throw new LoginTemporarilyLockedException(seconds);
            }
        }
    }

    public void recordFailure(String key) {
        if (key == null) return;
        Instant now = clock.instant();
        if (failures.size() >= MAX_KEYS) pruneAll(now);
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    public void recordSuccess(String key) {
        if (key != null) failures.remove(key);
    }

    int trackedKeys() {
        return failures.size();
    }

    private static void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) attempts.pollFirst();
    }

    private void pruneAll(Instant now) {
        failures.entrySet().removeIf(entry -> {
            Deque<Instant> attempts = entry.getValue();
            synchronized (attempts) {
                prune(attempts, now);
                return attempts.isEmpty();
            }
        });
        if (failures.size() >= MAX_KEYS) failures.clear(); // 그래도 가득 차면 메모리를 지키기 위해 비운다
    }
}
