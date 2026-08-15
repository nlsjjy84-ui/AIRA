package com.aira.api.auth.controller;

import com.aira.api.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AuthRequestBodyLimitFilter extends OncePerRequestFilter {
    private static final byte[] TOO_LARGE_RESPONSE = (
            "{\"code\":\"INVALID_REQUEST\",\"message\":\"입력값을 확인해주세요.\","
                    + "\"errors\":[{\"field\":\"request\",\"message\":\"요청 본문이 너무 큽니다.\"}]}")
            .getBytes(StandardCharsets.UTF_8);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/auth/signup") && !path.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > AuthProperties.REQUEST_BODY_MAX_BYTES) {
            reject(response);
            return;
        }
        byte[] body = readAtMost(request, AuthProperties.REQUEST_BODY_MAX_BYTES + 1);
        if (body.length > AuthProperties.REQUEST_BODY_MAX_BYTES) {
            reject(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static byte[] readAtMost(HttpServletRequest request, int limit) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(limit, 1024));
        byte[] buffer = new byte[1024];
        ServletInputStream input = request.getInputStream();
        while (body.size() < limit) {
            int read = input.read(buffer, 0, Math.min(buffer.length, limit - body.size()));
            if (read < 0) break;
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(TOO_LARGE_RESPONSE.length);
        response.getOutputStream().write(TOO_LARGE_RESPONSE);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async request reading is not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
