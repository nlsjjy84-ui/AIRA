package com.aira.api.auth.dto;

import com.aira.api.user.domain.AppUser;
import java.util.UUID;

public record LoginResponse(User user) {
    public static LoginResponse from(AppUser user) {
        return new LoginResponse(new User(user.getId(), user.getNickname()));
    }

    public record User(UUID id, String nickname) {}
}
