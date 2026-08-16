package com.aira.api.auth.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.exception.InvalidPasswordResetTokenException;
import com.aira.api.auth.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordResetControllerTests {
    PasswordResetService service = mock(PasswordResetService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PasswordResetController(service))
                .setControllerAdvice(new AuthExceptionHandler()).build();
    }

    @Test
    void requestAlwaysReturnsAcceptedWithoutToken() throws Exception {
        mvc.perform(post("/api/auth/password-reset/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        verify(service).request("unknown@example.com");
    }

    @Test
    void confirmReturnsNoContentOnSuccess() throws Exception {
        mvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"newPassword\":\"new-password-value\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidTokenUsesGenericFailureWithoutInternalReason() throws Exception {
        doThrow(new InvalidPasswordResetTokenException()).when(service)
                .confirm("invalid-token", "new-password-value");
        mvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid-token\",\"newPassword\":\"new-password-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }
}
