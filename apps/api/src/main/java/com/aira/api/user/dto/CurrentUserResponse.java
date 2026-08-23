package com.aira.api.user.dto;

import java.util.UUID;

public record CurrentUserResponse(UUID userId, String nickname) {}
