package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.CountryEntityRegistrationStore;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KoreaCountryBootstrapOperationTests {
    @Mock CountryEntityRegistrationStore registrations;
    @Mock MarketEntityRepository entities;

    private KoreaCountryBootstrapOperation operation;
    private UUID id;

    @BeforeEach
    void setUp() {
        operation = new KoreaCountryBootstrapOperation(registrations, entities);
        id = UUID.randomUUID();
    }

    @Test
    void registersAndReturnsExactKoreaContract() throws Exception {
        stubCanonicalCountry();

        var result = operation.registerOrReuse();

        verify(registrations).registerOrGetId(KoreaCountryBootstrapOperation.KOREA);
        assertEquals(id, result.entityId());
        assertEquals("COUNTRY:KR", result.canonicalKey());
        assertEquals("Republic of Korea", result.canonicalName());
        assertEquals("KR", result.countryCode());
    }

    @Test
    void rejectsDriftedStoredCountry() throws Exception {
        when(registrations.registerOrGetId(KoreaCountryBootstrapOperation.KOREA)).thenReturn(id);
        when(entities.findById(id)).thenReturn(Optional.of(country(
                id, EntityType.COUNTRY, "Korea", "COUNTRY:KR", "KR", true)));

        assertThrows(IllegalStateException.class, operation::registerOrReuse);
    }

    @Test
    void rejectsInactiveStoredCountry() throws Exception {
        when(registrations.registerOrGetId(KoreaCountryBootstrapOperation.KOREA)).thenReturn(id);
        when(entities.findById(id)).thenReturn(Optional.of(country(
                id, EntityType.COUNTRY, "Republic of Korea", "COUNTRY:KR", "KR", false)));

        assertThrows(IllegalStateException.class, operation::registerOrReuse);
    }

    @Test
    void registrationContractRejectsInvalidCountryCodesAndBuildsCanonicalKey() {
        var registration = new CountryEntityRegistration(" Republic of Korea ", "KR");
        assertEquals("Republic of Korea", registration.canonicalName());
        assertEquals("KR", registration.countryCode());
        assertEquals("COUNTRY:KR", registration.canonicalKey());
        assertThrows(IllegalArgumentException.class,
                () -> new CountryEntityRegistration("Republic of Korea", "kr"));
        assertThrows(IllegalArgumentException.class,
                () -> new CountryEntityRegistration("Republic of Korea", "KOR"));
    }

    @Test
    void bootstrapHasNoEcosOrExternalIdentifierDependency() {
        for (Field field : KoreaCountryBootstrapOperation.class.getDeclaredFields()) {
            String type = field.getType().getName();
            org.junit.jupiter.api.Assertions.assertFalse(type.contains("ecos"));
            org.junit.jupiter.api.Assertions.assertFalse(type.contains("ExternalIdentifier"));
        }
    }

    private void stubCanonicalCountry() throws Exception {
        when(registrations.registerOrGetId(KoreaCountryBootstrapOperation.KOREA)).thenReturn(id);
        when(entities.findById(id)).thenReturn(Optional.of(country(
                id, EntityType.COUNTRY, "Republic of Korea", "COUNTRY:KR", "KR", true)));
    }

    private static MarketEntity country(UUID id, EntityType type, String name, String key,
            String countryCode, boolean active) throws Exception {
        var constructor = MarketEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        MarketEntity entity = constructor.newInstance();
        set(entity, "id", id);
        set(entity, "entityType", type);
        set(entity, "canonicalName", name);
        set(entity, "canonicalKey", key);
        set(entity, "countryCode", countryCode);
        set(entity, "active", active);
        return entity;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
