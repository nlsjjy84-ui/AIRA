package com.aira.api.delivery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class SpaPageControllerTests {
    @Test
    void forwardsPublicSpaRoutesToTheBuiltFrontend() throws Exception {
        var controller = new SpaPageController();
        assertEquals("forward:/index.html", controller.forwardSpaRoute());

        var mapping = SpaPageController.class
                .getMethod("forwardSpaRoute")
                .getAnnotation(GetMapping.class);
        assertArrayEquals(new String[]{"/explore", "/finance", "/privacy"}, mapping.value());
    }
}
