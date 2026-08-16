package com.aira.api.auth.email;

@FunctionalInterface
public interface ResendGateway {
    void send(String from, String to, String subject, String html);
}
