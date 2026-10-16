package com.aira.api.auth.security;

import com.aira.api.auth.service.DemoLoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** The shared demo account can read everything and use AI reading help, but cannot change anything. */
public final class DemoReadOnlyFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AiraPrincipal principal
                && DemoLoginService.isDemoNickname(principal.nickname())
                && !SAFE_METHODS.contains(request.getMethod())
                && !allowed(request.getRequestURI().substring(request.getContextPath().length()))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"DEMO_READ_ONLY\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    // Briefing and alert reconcile are POSTs that only assemble read views from stored analyses.
    static boolean allowed(String path) {
        // 결제 시험: 데모 계정은 승인 직후 전액 취소되고 플러스는 켜지지 않는다(PlanPaymentService).
        return path.startsWith("/api/me/assist/")
                || path.startsWith("/api/me/payments/")
                || path.equals("/api/me/briefings/current")
                || path.equals("/api/me/alerts/reconcile")
                || path.equals("/api/auth/logout")
                || path.equals("/api/auth/demo");
    }
}
