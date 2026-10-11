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
    private static final List<String> PREFERRED_COMPANIES = List.of("삼성전자", "SK하이닉스", "NAVER", "현대자동차");
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

    /** Hourly AI call limit for this user: the normal limit, or a larger shared one for the demo account. */
    public static int hourlyLimit(UUID userId, int normalLimit) {
        UUID demo = demoUserId;
        return demo != null && demo.equals(userId) ? normalLimit * DEMO_QUOTA_MULTIPLIER : normalLimit;
    }

    static void rememberDemoUser(UUID id) { demoUserId = id; }

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

    /** Gives the demo a few saved companies so briefing/alerts have something to show. Never fails the login. */
    private void seedInterests(UUID userId) {
        try {
            if (!interests.findAll(userId).isEmpty()) return;
            var companies = entities.findPubliclyAvailableCompanies();
            var picked = companies.stream().filter(c -> PREFERRED_COMPANIES.contains(c.getCanonicalName())).limit(3).toList();
            if (picked.isEmpty()) picked = companies.stream().limit(3).toList();
            for (var company : picked) {
                interests.add(userId, company.getId());
                interests.setAlertEnabled(userId, company.getId(), true);
            }
        } catch (RuntimeException seedFailure) {
            log.warn("Demo interest seeding skipped: {}", seedFailure.getClass().getSimpleName());
        }
    }
}
