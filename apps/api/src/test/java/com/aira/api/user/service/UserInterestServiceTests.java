package com.aira.api.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.UserInterest;
import com.aira.api.user.exception.DuplicateUserInterestException;
import com.aira.api.user.exception.InvalidInterestEntityException;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.UserInterestRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

class UserInterestServiceTests {
    UserInterestRepository interests = mock(UserInterestRepository.class);
    MarketEntityRepository entities = mock(MarketEntityRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    UserInterestService service;
    UUID userId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserInterestService(interests, entities, users);
        when(users.findByIdForInterest(userId)).thenReturn(Optional.of(mock(AppUser.class)));
    }

    @Test
    void listsOnlyTheAuthenticatedUsersInterests() {
        when(interests.findAllByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertTrue(service.findAll(userId).isEmpty());

        verify(interests).findAllByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Test
    void addsAllowedActiveEntityWithV1Defaults() {
        MarketEntity entity = activeEntity(EntityType.COMPANY);
        AppUser user = mock(AppUser.class);
        when(entities.findById(entityId)).thenReturn(Optional.of(entity));
        when(users.getReferenceById(userId)).thenReturn(user);
        when(interests.saveAndFlush(any(UserInterest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.add(userId, entityId);

        assertEquals(entityId, response.entityId());
        assertNull(response.interestLevel());
        assertFalse(response.alertEnabled());
    }

    @Test
    void rejectsInactiveEntities() {
        MarketEntity inactive = mock(MarketEntity.class);
        when(inactive.isActive()).thenReturn(false);
        when(entities.findById(entityId)).thenReturn(Optional.of(inactive));
        assertThrows(InvalidInterestEntityException.class, () -> service.add(userId, entityId));
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = "COMPANY", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryActiveNonCompanyEntity(EntityType entityType) {
        MarketEntity entity = activeEntity(entityType);
        when(entities.findById(entityId)).thenReturn(Optional.of(entity));

        assertThrows(InvalidInterestEntityException.class, () -> service.add(userId, entityId));
    }

    @Test
    void returnsExistingInterestWithoutChangingItsActivationBaseline() {
        MarketEntity entity = activeEntity(EntityType.COMPANY);
        UserInterest existing = UserInterest.create(mock(AppUser.class), entity, OffsetDateTime.now());
        OffsetDateTime baseline = OffsetDateTime.now();
        existing.setAlertEnabled(true, baseline);
        when(interests.findByUser_IdAndMarketEntity_Id(userId, entityId)).thenReturn(Optional.of(existing));
        assertEquals(baseline, service.add(userId, entityId).alertEnabledAt());
        org.mockito.Mockito.verify(interests, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void deletesByAuthenticatedUserAndEntityTogether() {
        service.remove(userId, entityId);

        verify(interests).deleteByUser_IdAndMarketEntity_Id(userId, entityId);
    }

    @Test
    void explicitlyEnablesAndDisablesInAppAlerts() {
        UserInterest interest = UserInterest.create(mock(AppUser.class), activeEntity(EntityType.COMPANY),
                java.time.OffsetDateTime.now());
        when(interests.findByUser_IdAndMarketEntity_Id(userId, entityId)).thenReturn(Optional.of(interest));
        when(interests.saveAndFlush(interest)).thenReturn(interest);

        assertTrue(service.setAlertEnabled(userId, entityId, true).alertEnabled());
        assertFalse(service.setAlertEnabled(userId, entityId, false).alertEnabled());
    }

    @Test
    void alertActivationTransitionsHaveAnExplicitStableBaseline() {
        OffsetDateTime created = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        OffsetDateTime enabled = created.plusSeconds(10);
        OffsetDateTime retry = enabled.plusSeconds(10);
        OffsetDateTime disabled = retry.plusSeconds(10);
        OffsetDateTime reenabled = disabled.plusSeconds(10);
        UserInterest interest = UserInterest.create(mock(AppUser.class),
                activeEntity(EntityType.COMPANY), created);

        assertFalse(interest.isAlertEnabled());
        assertNull(interest.getAlertEnabledAt());
        interest.setAlertEnabled(true, enabled);
        assertTrue(interest.isAlertEnabled());
        assertEquals(enabled, interest.getAlertEnabledAt());
        assertEquals(enabled, interest.getUpdatedAt());

        interest.setAlertEnabled(true, retry);
        assertEquals(enabled, interest.getAlertEnabledAt());
        assertEquals(enabled, interest.getUpdatedAt());

        interest.setAlertEnabled(false, disabled);
        assertFalse(interest.isAlertEnabled());
        assertNull(interest.getAlertEnabledAt());
        assertEquals(disabled, interest.getUpdatedAt());

        interest.setAlertEnabled(true, reenabled);
        assertTrue(interest.isAlertEnabled());
        assertEquals(reenabled, interest.getAlertEnabledAt());
        assertEquals(reenabled, interest.getUpdatedAt());
    }

    private MarketEntity activeEntity(EntityType type) {
        MarketEntity entity = mock(MarketEntity.class);
        when(entity.getId()).thenReturn(entityId);
        when(entity.getEntityType()).thenReturn(type);
        when(entity.isActive()).thenReturn(true);
        return entity;
    }
}
