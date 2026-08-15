package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(SignupPersistenceService.class)
class SignupPersistenceServiceTests {
    @Autowired SignupPersistenceService persistence;
    @Autowired AppUserRepository users;
    @Autowired AuthenticationCredentialRepository credentials;

    @Test
    void persistsUserAndCredentialWithoutRawPassword() {
        String rawPassword = "correct horse battery staple";
        String hash = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";

        AppUser created = persistence.save("AiraUser", "airauser", hash);

        AppUser user = users.findById(created.getId()).orElseThrow();
        AuthenticationCredential credential = credentials.findAll().getFirst();
        assertEquals("AiraUser", user.getNickname());
        assertEquals("airauser", user.getNicknameNormalized());
        assertEquals(user.getId(), credential.getUser().getId());
        assertEquals(hash, credential.getPasswordHash());
        assertFalse(credential.getPasswordHash().contains(rawPassword));
    }
}
