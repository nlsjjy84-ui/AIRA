package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.config.AuthConfiguration;
import com.aira.api.auth.dto.LoginRequest;
import com.aira.api.auth.dto.SignupRequest;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.user.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AuthConfiguration.class, AuthService.class, SignupPersistenceService.class,
        LoginService.class, LoginPersistenceService.class})
class LoginIntegrationTests {
    @Autowired AuthService signup;
    @Autowired LoginService login;
    @Autowired UserSessionRepository sessions;

    @Test
    void signsUpAndLogsInEnglishKoreanAndNfkcEquivalentNicknameWithRealArgon2() {
        String password = "correct-password-value";
        signup.signup(new SignupRequest("AiraUser", password));
        signup.signup(new SignupRequest("테스트1", password));

        assertEquals("AiraUser", login.login(new LoginRequest("ＡＩＲＡuser", password))
                .response().user().nickname());
        assertEquals("테스트1", login.login(new LoginRequest("테스트1", password))
                .response().user().nickname());
        assertEquals(2, sessions.count());
    }

    @Test
    void authenticationFailuresCreateNoSession() {
        String password = "correct-password-value";
        signup.signup(new SignupRequest("AiraUser", password));

        assertThrows(AuthenticationFailedException.class,
                () -> login.login(new LoginRequest("UnknownUser", password)));
        assertThrows(AuthenticationFailedException.class,
                () -> login.login(new LoginRequest("AiraUser", "wrong-password-value")));
        assertEquals(0, sessions.count());
    }
}
