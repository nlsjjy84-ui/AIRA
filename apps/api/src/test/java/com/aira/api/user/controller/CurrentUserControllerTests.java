package com.aira.api.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CurrentUserControllerTests {
    @Test
    void returnsOnlyMinimalAuthenticatedIdentity() {
        UUID userId = UUID.randomUUID();
        var response = new CurrentUserController().find(new AiraPrincipal(userId, "AiraUser"));

        assertEquals(userId, response.userId());
        assertEquals("AiraUser", response.nickname());
        assertEquals(Set.of("userId", "nickname"), Arrays.stream(response.getClass()
                .getRecordComponents()).map(component -> component.getName())
                .collect(Collectors.toSet()));
    }
}
