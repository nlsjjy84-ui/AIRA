package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import com.aira.api.market.repository.EntityExternalIdentifierRegistrationStore;
import com.aira.api.market.repository.EntityExternalIdentifierRegistrationStore.StoredExternalIdentifier;
import com.aira.api.market.repository.EntityExternalIdentifierRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityExternalIdentifierRegistryServiceTests {
    @Mock private MarketEntityRepository entities;
    @Mock private EntityExternalIdentifierRepository identifiers;
    @Mock private EntityExternalIdentifierRegistrationStore registrations;

    private EntityExternalIdentifierRegistryService service;
    private MarketEntity company;
    private EntityExternalIdentifier identifier;
    private UUID identifierId;

    @BeforeEach
    void setUp() throws Exception {
        service = new EntityExternalIdentifierRegistryService(
                entities, identifiers, registrations);
        company = entity(EntityType.COMPANY);
        identifierId = UUID.randomUUID();
        identifier = externalIdentifier(identifierId, company,
                "OPENDART", "CORP_CODE", "00126380");
        lenient().when(entities.findById(company.getId())).thenReturn(Optional.of(company));
        lenient().when(registrations.registerOrGet(any())).thenReturn(
                new StoredExternalIdentifier(identifierId, company.getId()));
        lenient().when(identifiers.findById(identifierId)).thenReturn(Optional.of(identifier));
    }

    @Test
    void registersFirstMapping() {
        assertSame(identifier, service.registerOrReuse(opendart(company.getId(), "00126380")));
        verify(registrations).registerOrGet(any());
    }

    @Test
    void repeatedMappingReusesStoredIdentifier() {
        ExternalIdentifierRegistration registration = opendart(company.getId(), "00126380");

        assertSame(identifier, service.registerOrReuse(registration));
        assertSame(identifier, service.registerOrReuse(registration));
        verify(registrations, org.mockito.Mockito.times(2)).registerOrGet(registration);
    }

    @Test
    void remappingToAnotherEntityIsRejected() throws Exception {
        MarketEntity other = entity(EntityType.COMPANY);
        when(entities.findById(other.getId())).thenReturn(Optional.of(other));
        when(registrations.registerOrGet(any())).thenReturn(
                new StoredExternalIdentifier(identifierId, company.getId()));

        assertThrows(ExternalIdentifierConflictException.class,
                () -> service.registerOrReuse(opendart(other.getId(), "00126380")));
    }

    @Test
    void sameValueInDifferentNamespacesRemainsDistinct() throws Exception {
        EntityExternalIdentifier other = externalIdentifier(UUID.randomUUID(), company,
                "SEC", "CIK", "00126380");
        when(registrations.registerOrGet(any())).thenReturn(
                new StoredExternalIdentifier(other.getId(), company.getId()));
        when(identifiers.findById(other.getId())).thenReturn(Optional.of(other));

        assertSame(other, service.registerOrReuse(new ExternalIdentifierRegistration(
                company.getId(), new ExternalIdentifierKey("SEC", "CIK", "00126380"))));
    }

    @Test
    void oneEntityCanHaveMultipleIdentifiers() throws Exception {
        EntityExternalIdentifier other = externalIdentifier(UUID.randomUUID(), company,
                "OPENDART", "CORP_CODE", "00000001");
        when(registrations.registerOrGet(any())).thenReturn(
                new StoredExternalIdentifier(other.getId(), company.getId()));
        when(identifiers.findById(other.getId())).thenReturn(Optional.of(other));

        assertSame(other, service.registerOrReuse(opendart(company.getId(), "00000001")));
    }

    @Test
    void externalIdentifierLooksUpEntity() {
        ExternalIdentifierKey key = new ExternalIdentifierKey(
                "OPENDART", "CORP_CODE", "00126380");
        when(identifiers.findByNamespaceAndIdentifierTypeAndIdentifierValue(
                "OPENDART", "CORP_CODE", "00126380"))
                .thenReturn(Optional.of(identifier));

        assertSame(company, service.findEntity(key).orElseThrow());
    }

    @Test
    void entityLooksUpAllIdentifiers() {
        when(identifiers.findAllByEntity_IdOrderByNamespaceAscIdentifierTypeAscIdentifierValueAsc(
                company.getId())).thenReturn(List.of(identifier));

        assertEquals(List.of(identifier), service.findIdentifiers(company.getId()));
    }

    @Test
    void nullAndBlankInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentifierKey(null, "TYPE", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentifierKey(" ", "TYPE", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentifierKey("NS", " ", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentifierKey("NS", "TYPE", " "));
        assertThrows(IllegalArgumentException.class, () -> service.registerOrReuse(null));
        assertThrows(IllegalArgumentException.class, () -> service.findEntity(null));
        assertThrows(IllegalArgumentException.class, () -> service.findIdentifiers(null));
    }

    @Test
    void opendartCorpCodePreservesLeadingZero() {
        ExternalIdentifierRegistration registration = opendart(company.getId(), "00126380");

        service.registerOrReuse(registration);

        ArgumentCaptor<ExternalIdentifierRegistration> saved =
                ArgumentCaptor.forClass(ExternalIdentifierRegistration.class);
        verify(registrations).registerOrGet(saved.capture());
        assertEquals("00126380", saved.getValue().identifier().identifierValue());
        assertThrows(IllegalArgumentException.class,
                () -> opendart(company.getId(), "126380"));
    }

    @Test
    void opendartCorpCodeRejectsSecurityEntity() throws Exception {
        MarketEntity security = entity(EntityType.SECURITY);
        when(entities.findById(security.getId())).thenReturn(Optional.of(security));

        assertThrows(IllegalArgumentException.class,
                () -> service.registerOrReuse(opendart(security.getId(), "00126380")));
        verify(registrations, never()).registerOrGet(any());
    }

    private static ExternalIdentifierRegistration opendart(UUID entityId, String corpCode) {
        return new ExternalIdentifierRegistration(entityId,
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", corpCode));
    }

    private static MarketEntity entity(EntityType type) throws Exception {
        MarketEntity entity = instance(MarketEntity.class);
        set(entity, "id", UUID.randomUUID());
        set(entity, "entityType", type);
        return entity;
    }

    private static EntityExternalIdentifier externalIdentifier(UUID id, MarketEntity entity,
            String namespace, String type, String value) throws Exception {
        EntityExternalIdentifier identifier = instance(EntityExternalIdentifier.class);
        set(identifier, "id", id);
        set(identifier, "entity", entity);
        set(identifier, "namespace", namespace);
        set(identifier, "identifierType", type);
        set(identifier, "identifierValue", value);
        return identifier;
    }

    private static <T> T instance(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
