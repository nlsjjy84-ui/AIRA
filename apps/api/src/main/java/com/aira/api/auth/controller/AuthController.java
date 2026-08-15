package com.aira.api.auth.controller;

import com.aira.api.auth.dto.LoginRequest;
import com.aira.api.auth.dto.LoginResponse;
import com.aira.api.auth.dto.SignupRequest;
import com.aira.api.auth.dto.SignupResponse;
import com.aira.api.auth.service.AuthService;
import com.aira.api.auth.service.LoginResult;
import com.aira.api.auth.service.LoginService;
import com.aira.api.auth.security.SessionCookieFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginService loginService;
    private final SessionCookieFactory sessionCookieFactory;

    public AuthController(AuthService authService, LoginService loginService,
            SessionCookieFactory sessionCookieFactory) {
        this.authService = authService;
        this.loginService = loginService;
        this.sessionCookieFactory = sessionCookieFactory;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.create(result.rawSessionToken()).toString())
                .body(result.response());
    }
}
