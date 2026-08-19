package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyEntityBootstrapOperationTests {
    private static final UUID FIRST_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private MarketEntityRepository entities;
    private CompanyEntityBootstrapOperation operation;
    private AtomicInteger sequence;

    @BeforeEach
    void setUp() throws Exception {
        sequence = new AtomicInteger();
        operation = new CompanyEntityBootstrapOperation(entities,
                Clock.fixed(Instant.parse("2026-08-19T06:00:00Z"), ZoneOffset.UTC),
                () -> sequence.getAndIncrement() == 0 ? FIRST_KEY : SECOND_KEY);
        org.mockito.Mockito.lenient().when(entities.saveAndFlush(any())).thenAnswer(invocation -> {
            MarketEntity value = invocation.getArgument(0);
            set(value, "id", UUID.randomUUID());
            return value;
        });
    }

    @Test
    void createsActiveCompanyWithOpaqueCanonicalIdentity() {
        var result = operation.create(new CompanyEntityBootstrapCommand("Example Company", "KR"));

        var captor = ArgumentCaptor.forClass(MarketEntity.class);
        verify(entities).saveAndFlush(captor.capture());
        var company = captor.getValue();
        assertEquals(EntityType.COMPANY, company.getEntityType());
        assertTrue(company.isActive());
        assertEquals("COMPANY:" + FIRST_KEY, result.canonicalKey());
        assertEquals("Example Company", result.canonicalName());
        assertEquals("KR", result.countryCode());
    }

    @Test
    void canonicalKeyDoesNotDependOnCompanyNameOrProviderIdentifiers() {
        var result = operation.create(new CompanyEntityBootstrapCommand(
                "00126380 Samsung 005930", null));

        assertEquals("COMPANY:" + FIRST_KEY, result.canonicalKey());
        assertFalse(result.canonicalKey().contains("00126380"));
        assertFalse(result.canonicalKey().contains("005930"));
        assertFalse(result.canonicalKey().contains("Samsung"));
        assertNull(result.countryCode());
    }

    @Test
    void twoCompaniesReceiveDifferentCanonicalIdentities() {
        var first = operation.create(new CompanyEntityBootstrapCommand("First"));
        var second = operation.create(new CompanyEntityBootstrapCommand("Second"));

        assertNotEquals(first.entityId(), second.entityId());
        assertNotEquals(first.canonicalKey(), second.canonicalKey());
    }

    @Test
    void identicalNamesAreExplicitlyCreatedWithoutMergeOrReuse() {
        var first = operation.create(new CompanyEntityBootstrapCommand("Same Name"));
        var second = operation.create(new CompanyEntityBootstrapCommand("Same Name"));

        assertNotEquals(first.entityId(), second.entityId());
        assertNotEquals(first.canonicalKey(), second.canonicalKey());
    }

    @Test
    void invalidNamesAreRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class,
                () -> operation.create(new CompanyEntityBootstrapCommand(null)));
        assertThrows(IllegalArgumentException.class,
                () -> operation.create(new CompanyEntityBootstrapCommand("   ")));
        assertThrows(IllegalArgumentException.class,
                () -> operation.create(new CompanyEntityBootstrapCommand("x".repeat(301))));
        verify(entities, never()).saveAndFlush(any());
    }

    @Test
    void countryCodeIsOptionalButMustMatchExistingConstraint() {
        assertThrows(IllegalArgumentException.class,
                () -> operation.create(new CompanyEntityBootstrapCommand("Company", "kr")));
        assertThrows(IllegalArgumentException.class,
                () -> operation.create(new CompanyEntityBootstrapCommand("Company", "KOR")));
        verify(entities, never()).saveAndFlush(any());
    }

    @Test
    void entityHasNoCanonicalKeyMutationMethod() {
        assertTrue(java.util.Arrays.stream(MarketEntity.class.getMethods())
                .noneMatch(method -> method.getName().equals("setCanonicalKey")));
    }

    @Test
    void operationCreatesOnlyCompanyAndHasNoExternalIdentifierDependency() {
        assertEquals(EntityType.COMPANY,
                MarketEntity.company("Company", null, FIRST_KEY,
                        java.time.OffsetDateTime.now()).getEntityType());
        assertTrue(java.util.Arrays.stream(CompanyEntityBootstrapOperation.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName()
                        .contains("ExternalIdentifier")));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
