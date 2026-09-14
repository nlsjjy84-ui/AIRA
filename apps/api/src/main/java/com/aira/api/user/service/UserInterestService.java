package com.aira.api.user.service;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.krx.KrxCurrentQuery;
import com.aira.api.market.krx.KrxCurrentExactMissException;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.user.domain.UserInterest;
import com.aira.api.user.dto.UserInterestResponse;
import com.aira.api.user.dto.InterestEligibilityResponse;
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
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInterestService {
    // COMPANY eligibility is independent; SECURITY uses its own exact official market gate.
    private static final Set<EntityType> V1_ENTITY_TYPES =
            EnumSet.of(EntityType.COMPANY);

    private final UserInterestRepository interests;
    private final MarketEntityRepository entities;
    private final AppUserRepository users;
    private final KrxCurrentQuery krxCurrent;

    @Autowired
    public UserInterestService(UserInterestRepository interests, MarketEntityRepository entities,
            AppUserRepository users, KrxCurrentQuery krxCurrent) {
        this.interests = interests;
        this.entities = entities;
        this.users = users;
        this.krxCurrent = krxCurrent;
    }

    UserInterestService(UserInterestRepository interests, MarketEntityRepository entities,
            AppUserRepository users) { this(interests, entities, users, null); }

    @Transactional(readOnly = true)
    public List<UserInterestResponse> findAll(UUID userId) {
        return interests.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(UserInterestService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterestEligibilityResponse eligibility(UUID userId, UUID entityId) {
        users.findByIdForInterest(userId).orElseThrow();
        var entity = entities.findById(entityId).orElse(null);
        if (entity == null) return new InterestEligibilityResponse(CanonicalDataState.NO_DATA,
                entityId, null, false, false, null, "ENTITY_NOT_FOUND");
        boolean existing = interests.findByUser_IdAndMarketEntity_Id(userId, entityId).isPresent();
        if (existing) return new InterestEligibilityResponse(CanonicalDataState.AVAILABLE,
                entityId, entity.getEntityType(), true, false, null, "ALREADY_INTERESTED");
        try { validate(entity); }
        catch (InvalidInterestEntityException invalid) {
            return new InterestEligibilityResponse(CanonicalDataState.UNSUPPORTED,
                    entityId, entity.getEntityType(), false, false, null, "UNSUPPORTED_TARGET");
        }
        if (entity.getEntityType() == EntityType.COMPANY)
            return new InterestEligibilityResponse(CanonicalDataState.AVAILABLE,
                    entityId, entity.getEntityType(), false, true, null, null);
        if (krxCurrent == null) return new InterestEligibilityResponse(CanonicalDataState.UNAVAILABLE,
                entityId, entity.getEntityType(), false, false, null, "KRX_CURRENT_UNAVAILABLE");
        try {
            var observation = krxCurrent.eligibility(entityId);
            boolean restricted = observation.close().value().compareTo(new BigDecimal("1000")) < 0
                    || observation.dailyChangePercent().compareTo(new BigDecimal("20")) >= 0;
            // Eligibility applies only to a new private Interest; it does not rewrite stored Interest.
            return new InterestEligibilityResponse(restricted ? CanonicalDataState.BLOCKED : CanonicalDataState.AVAILABLE,
                    entityId, entity.getEntityType(), false, !restricted, observation.close().tradingDate(),
                    restricted ? "MARKET_THRESHOLD" : null);
        } catch (KrxCurrentExactMissException missing) {
            return new InterestEligibilityResponse(CanonicalDataState.NO_DATA,
                    entityId, entity.getEntityType(), false, false, null, "EXACT_D_FACT_MISSING");
        } catch (RuntimeException unavailable) {
            return new InterestEligibilityResponse(CanonicalDataState.UNAVAILABLE,
                    entityId, entity.getEntityType(), false, false, null, "KRX_CURRENT_UNAVAILABLE");
        }
    }

    @Transactional
    public UserInterestResponse add(UUID userId, UUID entityId) {
        users.findByIdForInterest(userId).orElseThrow();
        var existing = interests.findByUser_IdAndMarketEntity_Id(userId, entityId);
        if (existing.isPresent()) return toResponse(existing.get());
        MarketEntity entity = entities.findById(entityId)
                .orElseThrow(InterestEntityNotFoundException::new);
        validate(entity);
        if (entity.getEntityType() == EntityType.SECURITY) {
            if (krxCurrent == null) throw new InvalidInterestEntityException("KRX Current is unavailable");
            final KrxCurrentQuery.Eligibility observation;
            try { observation = krxCurrent.eligibility(entityId); }
            catch (RuntimeException unavailable) { throw new InvalidInterestEntityException("Exact official KRX D observation is required"); }
            // Only NEW interests are gated; existing rows returned above are never removed or reclassified.
            if (observation.close().value().compareTo(new BigDecimal("1000")) < 0
                    || observation.dailyChangePercent().compareTo(new BigDecimal("20")) >= 0)
                throw new InvalidInterestEntityException("Security is not eligible for new Interest");
        }

        UserInterest interest = UserInterest.create(
                users.getReferenceById(userId), entity, OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return toResponse(interests.saveAndFlush(interest));
    }

    @Transactional
    public void remove(UUID userId, UUID entityId) {
        users.findByIdForInterest(userId).orElseThrow();
        interests.deleteByUser_IdAndMarketEntity_Id(userId, entityId);
    }

    @Transactional
    public UserInterestResponse setAlertEnabled(UUID userId, UUID entityId, boolean enabled) {
        users.findByIdForInterest(userId).orElseThrow();
        UserInterest interest = interests.findByUser_IdAndMarketEntity_Id(userId, entityId)
                .orElseThrow(InterestEntityNotFoundException::new);
        interest.setAlertEnabled(enabled, OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return toResponse(interests.saveAndFlush(interest));
    }

    private static void validate(MarketEntity entity) {
        if (!entity.isActive()) {
            throw new InvalidInterestEntityException("Inactive entities cannot be added");
        }
        if (!V1_ENTITY_TYPES.contains(entity.getEntityType()) && entity.getEntityType() != EntityType.SECURITY) {
            throw new InvalidInterestEntityException("Entity type is not supported in v1");
        }
        if (entity.getEntityType() == EntityType.SECURITY
                && !("KOSPI".equals(entity.getMarketCode()) || "KOSDAQ".equals(entity.getMarketCode())))
            throw new InvalidInterestEntityException("Only official KOSPI/KOSDAQ stock family is supported");
    }

    private static UserInterestResponse toResponse(UserInterest interest) {
        MarketEntity entity = interest.getMarketEntity();
        return new UserInterestResponse(entity.getId(), entity.getEntityType(),
                entity.getCanonicalName(), entity.getMarketCode(), entity.getSymbol(),
                entity.getCountryCode(), interest.getInterestLevel(), interest.isAlertEnabled(),
                interest.getAlertEnabledAt(), interest.getCreatedAt());
    }
}
