package com.aira.api.auth.dto;

import com.aira.api.user.domain.AppUser;
import java.util.UUID;

public record SignupResponse(User user) {
    public static SignupResponse from(AppUser user) {
        return new SignupResponse(new User(user.getId(), user.getNickname()));
    }

    public record User(UUID id, String nickname) {}
}
