package com.aira.api.user.service;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.user.domain.UserInterest;
import com.aira.api.user.dto.UserInterestResponse;
import com.aira.api.user.exception.DuplicateUserInterestException;
import com.aira.api.user.exception.InterestEntityNotFoundException;
import com.aira.api.user.exception.InvalidInterestEntityException;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.UserInterestRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInterestService {
    private static final Set<EntityType> V1_ENTITY_TYPES =
            EnumSet.of(EntityType.COMPANY);

    private final UserInterestRepository interests;
    private final MarketEntityRepository entities;
    private final AppUserRepository users;

    public UserInterestService(UserInterestRepository interests, MarketEntityRepository entities,
            AppUserRepository users) {
        this.interests = interests;
        this.entities = entities;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserInterestResponse> findAll(UUID userId) {
        return interests.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(UserInterestService::toResponse)
                .toList();
    }

    @Transactional
    public UserInterestResponse add(UUID userId, UUID entityId) {
        MarketEntity entity = entities.findById(entityId)
                .orElseThrow(InterestEntityNotFoundException::new);
        validate(entity);
        if (interests.existsByUser_IdAndMarketEntity_Id(userId, entityId)) {
            throw new DuplicateUserInterestException();
        }

        UserInterest interest = UserInterest.create(
                users.getReferenceById(userId), entity, OffsetDateTime.now(ZoneOffset.UTC));
        try {
            return toResponse(interests.saveAndFlush(interest));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUserInterestException();
        }
    }

    @Transactional
    public void remove(UUID userId, UUID entityId) {
        interests.deleteByUser_IdAndMarketEntity_Id(userId, entityId);
    }

    @Transactional
    public UserInterestResponse setAlertEnabled(UUID userId, UUID entityId, boolean enabled) {
        UserInterest interest = interests.findByUser_IdAndMarketEntity_Id(userId, entityId)
                .orElseThrow(InterestEntityNotFoundException::new);
        interest.setAlertEnabled(enabled, OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(interests.saveAndFlush(interest));
    }

    private static void validate(MarketEntity entity) {
        if (!entity.isActive()) {
            throw new InvalidInterestEntityException("Inactive entities cannot be added");
        }
        if (!V1_ENTITY_TYPES.contains(entity.getEntityType())) {
            throw new InvalidInterestEntityException("Entity type is not supported in v1");
        }
    }

    private static UserInterestResponse toResponse(UserInterest interest) {
        MarketEntity entity = interest.getMarketEntity();
        return new UserInterestResponse(entity.getId(), entity.getEntityType(),
                entity.getCanonicalName(), entity.getMarketCode(), entity.getSymbol(),
                entity.getCountryCode(), interest.getInterestLevel(), interest.isAlertEnabled(),
                interest.getAlertEnabledAt(), interest.getCreatedAt());
    }
}
