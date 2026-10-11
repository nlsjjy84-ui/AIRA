package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DemoQuotaTests {
    @AfterEach
    void reset() { DemoLoginService.rememberDemoUser(null); }

    @Test
    void sharedDemoAccountGetsALargerHourlyAiQuota() {
        UUID demo = UUID.randomUUID();
        DemoLoginService.rememberDemoUser(demo);
        assertEquals(300, DemoLoginService.hourlyLimit(demo, 30));
    }

    @Test
    void normalUsersKeepTheNormalQuota() {
        DemoLoginService.rememberDemoUser(UUID.randomUUID());
        assertEquals(30, DemoLoginService.hourlyLimit(UUID.randomUUID(), 30));
    }

    @Test
    void beforeTheDemoAccountIsKnownEveryoneHasTheNormalQuota() {
        assertEquals(30, DemoLoginService.hourlyLimit(UUID.randomUUID(), 30));
    }
}
