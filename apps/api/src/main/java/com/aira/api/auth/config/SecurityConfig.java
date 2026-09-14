package com.aira.api.auth.config;

import com.aira.api.auth.security.SessionAuthenticationFilter;
import com.aira.api.auth.service.SessionAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {
    @Bean
    SessionAuthenticationFilter sessionAuthenticationFilter(
            SessionAuthenticationService authenticationService) {
        return new SessionAuthenticationFilter(authenticationService);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, SessionAuthenticationFilter sessionAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, failure) -> response.setStatus(401)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/recovery-email/verifications/confirm",
                                "/api/auth/password-reset/requests",
                                "/api/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/recovery-email/verifications").authenticated()
                        // Private ownership is checked behind this authenticated boundary, never by public read routes.
                        .requestMatchers("/api/me", "/api/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/search",
                                "/api/assessments/current",
                                "/api/securities/*/market-current",
                                "/api/securities/*/market-series",
                                "/api/securities/*/market-previous",
                                "/api/companies/*/financial-facts/exact",
                                "/api/companies/*/financial-facts/compare",
                                "/api/companies/*/financial-facts/current",
                                "/api/events",
                                "/api/events/*",
                                "/api/assessments/*",
                                "/api/evidence/*",
                                "/api/companies",
                                "/api/companies/*/financial-periods",
                                "/api/companies/*/financial-facts",
                                "/api/companies/*/events").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
