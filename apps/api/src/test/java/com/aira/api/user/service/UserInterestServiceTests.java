package com.aira.api.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        assertTrue(response.alertEnabled());
    }

    @Test
    void rejectsInactiveAndUnsupportedEntities() {
        MarketEntity inactive = mock(MarketEntity.class);
        when(inactive.isActive()).thenReturn(false);
        when(entities.findById(entityId)).thenReturn(Optional.of(inactive));
        assertThrows(InvalidInterestEntityException.class, () -> service.add(userId, entityId));

        MarketEntity market = activeEntity(EntityType.MARKET);
        when(entities.findById(entityId)).thenReturn(Optional.of(market));
        assertThrows(InvalidInterestEntityException.class, () -> service.add(userId, entityId));
    }

    @Test
    void convertsConcurrentUniqueViolationToDuplicateInterest() {
        MarketEntity entity = activeEntity(EntityType.SECURITY);
        when(entities.findById(entityId)).thenReturn(Optional.of(entity));
        when(users.getReferenceById(userId)).thenReturn(mock(AppUser.class));
        when(interests.saveAndFlush(any(UserInterest.class)))
                .thenThrow(new DataIntegrityViolationException("uq_user_interest_user_entity"));

        assertThrows(DuplicateUserInterestException.class, () -> service.add(userId, entityId));
    }

    @Test
    void deletesByAuthenticatedUserAndEntityTogether() {
        service.remove(userId, entityId);

        verify(interests).deleteByUser_IdAndMarketEntity_Id(userId, entityId);
    }

    private MarketEntity activeEntity(EntityType type) {
        MarketEntity entity = mock(MarketEntity.class);
        when(entity.getId()).thenReturn(entityId);
        when(entity.getEntityType()).thenReturn(type);
        when(entity.isActive()).thenReturn(true);
        return entity;
    }
}
