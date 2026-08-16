package com.aira.api.auth.security;

import com.aira.api.auth.service.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionAuthenticationService authenticationService;

    public SessionAuthenticationFilter(SessionAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/auth/signup")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            findSessionToken(request).flatMap(authenticationService::authenticate).ifPresent(principal -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, java.util.List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    private static java.util.Optional<String> findSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return java.util.Optional.empty();
        return Arrays.stream(cookies)
                .filter(cookie -> SessionCookieFactory.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
