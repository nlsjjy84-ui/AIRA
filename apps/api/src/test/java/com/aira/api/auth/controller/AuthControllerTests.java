package com.aira.api.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.dto.LoginResponse;
import com.aira.api.auth.dto.SignupResponse;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.auth.exception.InvalidSignupRequestException;
import com.aira.api.auth.exception.NicknameAlreadyExistsException;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.auth.config.AuthProperties;
import com.aira.api.auth.service.AuthService;
import com.aira.api.auth.service.LoginResult;
import com.aira.api.auth.service.LoginService;
import com.aira.api.auth.service.LogoutService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTests {
    AuthService service = mock(AuthService.class);
    LoginService loginService = mock(LoginService.class);
    LogoutService logoutService = mock(LogoutService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(service, loginService, logoutService,
                                new SessionCookieFactory(properties.getSession())))
                .addFilters(new AuthRequestBodyLimitFilter())
                .setControllerAdvice(new AuthExceptionHandler()).build();
    }

    @Test
    void signupReturns201PublicUserOnlyAndNoSessionCookie() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.signup(any())).thenReturn(new SignupResponse(new SignupResponse.User(id, "AiraUser")));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"AiraUser\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.nickname").value("AiraUser"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().doesNotExist("AIRA_SESSION"));
    }

    @Test
    void duplicateReturns409() throws Exception {
        when(service.signup(any())).thenThrow(new NicknameAlreadyExistsException());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"AiraUser\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("사용할 수 없는 닉네임입니다."));
    }

    @Test
    void validationReturns400WithErrors() throws Exception {
        when(service.signup(any())).thenThrow(new InvalidSignupRequestException("nickname"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"bad name\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void nicknameInvalidAfterNfkcReturns400() throws Exception {
        when(service.signup(any())).thenThrow(new InvalidSignupRequestException("nickname"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"１Aira\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void loginReturnsPublicUserAndSecureSessionCookie() throws Exception {
        UUID id = UUID.randomUUID();
        when(loginService.login(any())).thenReturn(new LoginResult(
                new LoginResponse(new LoginResponse.User(id, "AiraUser")), "opaque-token"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"AiraUser\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.nickname").value("AiraUser"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.session").doesNotExist())
                .andExpect(cookie().value("AIRA_SESSION", "opaque-token"))
                .andExpect(cookie().httpOnly("AIRA_SESSION", true))
                .andExpect(cookie().secure("AIRA_SESSION", true))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")));
    }

    @Test
    void unknownNicknameAndWrongPasswordReturnIdenticalFailure() throws Exception {
        when(loginService.login(any())).thenThrow(new AuthenticationFailedException());

        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"UnknownUser\",\"password\":\"secret-password-value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("AIRA_SESSION"))
                .andReturn().getResponse().getContentAsString();
        String wrong = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"AiraUser\",\"password\":\"wrong-password-value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("AIRA_SESSION"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknown, wrong);
    }

    @Test
    void loginNullFieldAndMalformedJsonUseExistingInvalidRequestFormat() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":null,\"password\":\"secret-password-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("request"));
    }

    @Test
    void oversizedSignupAndLoginAreRejectedBeforeController() throws Exception {
        String oversizedPassword = "x".repeat(AuthProperties.REQUEST_BODY_MAX_BYTES);
        String signupBody = "{\"nickname\":\"AiraUser\",\"password\":\"" + oversizedPassword + "\"}";
        String loginBody = "{\"nickname\":\"AiraUser\",\"password\":\"" + oversizedPassword + "\"}";

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("request"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("request"));

        verifyNoInteractions(service, loginService);
    }

    @Test
    void oversizedMalformedJsonIsRejectedByBodyLimit() throws Exception {
        String body = "{" + "x".repeat(AuthProperties.REQUEST_BODY_MAX_BYTES);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].message").value("요청 본문이 너무 큽니다."));

        verifyNoInteractions(loginService);
    }

    @Test
    void logoutRevokesPresentedTokenAndAlwaysDeletesCookie() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "opaque-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(SessionCookieFactory.COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(SessionCookieFactory.COOKIE_NAME, 0))
                .andExpect(cookie().httpOnly(SessionCookieFactory.COOKIE_NAME, true))
                .andExpect(cookie().secure(SessionCookieFactory.COOKIE_NAME, true));
        verify(logoutService).logout("opaque-token");
    }

    @Test
    void logoutWithoutCookieHasSameResponseAndDoesNotExposeState() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().maxAge(SessionCookieFactory.COOKIE_NAME, 0));
        verify(logoutService).logout(null);
    }
}
