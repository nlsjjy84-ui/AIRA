package com.aira.api.auth.service;

import com.aira.api.auth.dto.LoginResponse;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import com.aira.api.user.service.UserInterestService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * One-click read-only demo session so reviewers can see login-only features (interests, briefing,
 * alerts, AI reading help) without creating an account. The demo account has a random password that
 * nobody knows, its nickname is reserved for normal signup/login, and DemoReadOnlyFilter blocks writes.
 */
@Service
public class DemoLoginService implements ApplicationRunner {
    public static final String DEMO_NICKNAME = "데모계정";
    /**
     * 데모 계정 관심회사. 맨 앞이 화면 맨 위에 온다. 금융사(은행·증권·보험·카드)는 손익계산서 구조가 달라
     * 화면이 비기 쉬운 회사들이라, 면접관이 그런 회사도 바로 열어 볼 수 있게 함께 넣는다.
     */
    static final List<String> PREFERRED_COMPANIES = List.of(
            "NH투자증권", "KB금융", "신한지주", "하나금융지주", "우리금융지주", "기업은행",
            "BNK금융지주", "JB금융지주", "iM금융지주", "카카오뱅크", "삼성생명", "한화생명",
            "삼성화재해상보험", "DB손해보험", "현대해상", "미래에셋증권", "삼성증권", "키움증권", "한국금융지주",
            "메리츠금융지주", "삼성카드", "카카오페이",
            "삼성전자", "SK하이닉스", "NAVER", "현대자동차");
    private static final Logger log = LoggerFactory.getLogger(DemoLoginService.class);

    private final boolean enabled;
    private final AppUserRepository users;
    private final AuthenticationCredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final SignupPersistenceService signupPersistence;
    private final LoginPersistenceService loginPersistence;
    private final SessionTokenGenerator tokenGenerator;
    private final SessionTokenHasher tokenHasher;
    private final UserInterestService interests;
    private final MarketEntityRepository entities;
    private final SecureRandom random = new SecureRandom();
    /** Many reviewers share one demo account, so its AI hourly quota is larger than a normal user's. */
    static final int DEMO_QUOTA_MULTIPLIER = 10;
    private static volatile UUID demoUserId;
    private com.aira.api.stock.StudyClassController classes;

    /** 예시 공부 반(선택). 없거나 실패해도 데모 로그인은 그대로 된다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setClasses(com.aira.api.stock.StudyClassController classes) { this.classes = classes; }

    /** Hourly AI call limit for this user: the normal limit, or a larger shared one for the demo account. */
    public static int hourlyLimit(UUID userId, int normalLimit) {
        UUID demo = demoUserId;
        return demo != null && demo.equals(userId) ? normalLimit * DEMO_QUOTA_MULTIPLIER : normalLimit;
    }

    static void rememberDemoUser(UUID id) { demoUserId = id; }

    /** 공용 데모 계정인가(면접관이 함께 쓰므로 무료 한도를 걸지 않는다). */
    public static boolean isDemoUser(UUID userId) {
        UUID demo = demoUserId;
        return demo != null && demo.equals(userId);
    }

    /** After a restart, existing demo sessions keep working, so learn the demo account id at startup too. */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            users.findByNicknameNormalized(DEMO_NICKNAME).ifPresent(user -> rememberDemoUser(user.getId()));
        } catch (RuntimeException lookupFailure) {
            log.warn("Demo account lookup skipped: {}", lookupFailure.getClass().getSimpleName());
        }
    }

    public DemoLoginService(@Value("${aira.demo.login.enabled:${AIRA_DEMO_LOGIN_ENABLED:true}}") boolean enabled,
            AppUserRepository users, AuthenticationCredentialRepository credentials, PasswordHasher passwordHasher,
            SignupPersistenceService signupPersistence, LoginPersistenceService loginPersistence,
            SessionTokenGenerator tokenGenerator, SessionTokenHasher tokenHasher,
            UserInterestService interests, MarketEntityRepository entities) {
        this.enabled = enabled;
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.signupPersistence = signupPersistence;
        this.loginPersistence = loginPersistence;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.interests = interests;
        this.entities = entities;
    }

    public boolean isEnabled() { return enabled; }

    public static boolean isDemoNickname(String nickname) {
        return nickname != null && DEMO_NICKNAME.equals(nickname);
    }

    public LoginResult login() {
        if (!enabled) throw new IllegalStateException("Demo login is disabled");
        AppUser user = users.findByNicknameNormalized(DEMO_NICKNAME).orElseGet(this::create);
        AuthenticationCredential credential = credentials.findByUserId(user.getId()).orElseThrow();
        rememberDemoUser(user.getId());
        seedInterests(user.getId());
        seedClass(user.getId());
        String rawToken = tokenGenerator.generate();
        loginPersistence.save(user, credential, tokenHasher.hash(rawToken));
        return new LoginResult(LoginResponse.from(user), rawToken);
    }

    private AppUser create() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String unknowablePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            return signupPersistence.save(DEMO_NICKNAME, DEMO_NICKNAME, passwordHasher.hash(unknowablePassword));
        } catch (DataIntegrityViolationException raced) {
            return users.findByNicknameNormalized(DEMO_NICKNAME).orElseThrow(() -> raced);
        }
    }

    /** 면접관이 강사 화면(공부 반 진도표)을 볼 수 있게 예시 반을 하나 만든다. Never fails the login. */
    private void seedClass(UUID userId) {
        if (classes == null) return;
        try {
            var company = entities.findPubliclyAvailableCompanies().stream()
                    .filter(c -> "삼성전자".equals(c.getCanonicalName())).findFirst()
                    .or(() -> entities.findPubliclyAvailableCompanies().stream().findFirst());
            company.ifPresent(c -> classes.seedSample(userId, c.getId(), c.getCanonicalName()));
        } catch (RuntimeException seedFailure) {
            log.warn("Demo class seeding skipped: {}", seedFailure.getClass().getSimpleName());
        }
    }

    /**
     * Gives the demo saved companies so briefing/alerts have something to show. Adds only the preferred
     * companies it does not have yet, so a demo account made before the list grew gets the new ones too.
     * Never fails the login.
     */
    private void seedInterests(UUID userId) {
        try {
            var existing = interests.findAll(userId).stream().map(i -> i.entityId()).collect(java.util.stream.Collectors.toSet());
            var companies = entities.findPubliclyAvailableCompanies();
            var byName = new java.util.HashMap<String, UUID>();
            companies.forEach(c -> byName.putIfAbsent(c.getCanonicalName(), c.getId()));
            var toAdd = companiesToAdd(PREFERRED_COMPANIES, byName, existing);
            if (toAdd.isEmpty() && existing.isEmpty()) toAdd = companies.stream().limit(3).map(c -> c.getId()).toList();
            for (UUID companyId : toAdd) {
                try {
                    interests.add(userId, companyId);
                    interests.setAlertEnabled(userId, companyId, true);
                } catch (RuntimeException oneFailed) {
                    log.warn("Demo interest skipped: {}", oneFailed.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException seedFailure) {
            log.warn("Demo interest seeding skipped: {}", seedFailure.getClass().getSimpleName());
        }
    }

    /**
     * Preferred companies that exist and are not saved yet, in the order to add them. The list screen shows
     * the newest first, so the first preferred company is added last.
     */
    static List<UUID> companiesToAdd(List<String> preferred, java.util.Map<String, UUID> available,
            java.util.Set<UUID> existing) {
        var ids = new java.util.ArrayList<UUID>();
        for (int i = preferred.size() - 1; i >= 0; i--) {
            UUID id = available.get(preferred.get(i));
            if (id != null && !existing.contains(id) && !ids.contains(id)) ids.add(id);
        }
        return List.copyOf(ids);
    }
}
