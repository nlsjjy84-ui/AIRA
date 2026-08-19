package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenDartCompanyBootstrapOperationTests {
    @Mock private OpenDartCompanyDirectoryClient client;
    @Mock private MarketEntityRepository entities;
    @Mock private EntityExternalIdentifierRegistryService identifiers;

    private OpenDartCompanyBootstrapOperation operation;
    private OpenDartCompanyDirectoryRecord official;
    private UUID companyId;
    private MarketEntity company;

    @BeforeEach
    void setUp() throws Exception {
        operation = new OpenDartCompanyBootstrapOperation(client, entities, identifiers);
        official = new OpenDartCompanyDirectoryRecord("00126380", "삼성전자(주)",
                "SAMSUNG ELECTRONICS CO.,LTD.", "005930", LocalDate.of(2025, 8, 29));
        companyId = UUID.randomUUID();
        company = entity(companyId, EntityType.COMPANY);
        org.mockito.Mockito.lenient().when(client.fetch())
                .thenReturn(new OpenDartCompanyDirectory(List.of(official)));
        org.mockito.Mockito.lenient().when(entities.findById(companyId))
                .thenReturn(Optional.of(company));
        org.mockito.Mockito.lenient().when(identifiers.findEntity(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void defaultModeIsSafeValidDryRunWithoutRegistryWrite() {
        var command = new OpenDartCompanyBootstrapCommand(companyId, "00126380");

        var result = operation.execute(command);

        assertEquals(OpenDartCompanyBootstrapMode.DRY_RUN, command.mode());
        assertEquals(OpenDartCompanyBootstrapStatus.VALID_DRY_RUN, result.status());
        assertEquals("00126380", result.corpCode());
        assertEquals(official, result.officialRecord());
        assertFalse(result.existingMapping());
        assertNull(result.identifierId());
        verify(identifiers, never()).registerOrReuse(any());
    }

    @Test
    void explicitApplyRegistersThroughExistingRegistry() throws Exception {
        var stored = identifier(UUID.randomUUID());
        var registration = registration(companyId);
        when(identifiers.registerOrReuse(registration)).thenReturn(stored);

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        assertEquals(OpenDartCompanyBootstrapStatus.REGISTERED, result.status());
        assertEquals(stored.getId(), result.identifierId());
        verify(identifiers).registerOrReuse(registration);
    }

    @Test
    void sameMappingApplyIsIdempotentlyReusedThroughRegistry() throws Exception {
        when(identifiers.findEntity(any())).thenReturn(Optional.of(company));
        var stored = identifier(UUID.randomUUID());
        when(identifiers.registerOrReuse(registration(companyId))).thenReturn(stored);

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        assertEquals(OpenDartCompanyBootstrapStatus.REUSED, result.status());
        assertTrue(result.existingMapping());
        verify(identifiers).registerOrReuse(registration(companyId));
    }

    @Test
    void conflictingEntityReturnsConflictWithoutOverwrite() throws Exception {
        when(identifiers.findEntity(any()))
                .thenReturn(Optional.of(entity(UUID.randomUUID(), EntityType.COMPANY)));

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        assertEquals(OpenDartCompanyBootstrapStatus.CONFLICT, result.status());
        verify(identifiers, never()).registerOrReuse(any());
    }

    @Test
    void atomicRegistryRaceConflictIsReturnedWithoutRetry() {
        when(identifiers.registerOrReuse(registration(companyId)))
                .thenThrow(new ExternalIdentifierConflictException());

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        assertEquals(OpenDartCompanyBootstrapStatus.CONFLICT, result.status());
        verify(identifiers, times(1)).registerOrReuse(registration(companyId));
    }

    @Test
    void securityEntityIsRejectedBeforeDirectoryIdentityMatchingOrWrite() throws Exception {
        when(entities.findById(companyId)).thenReturn(
                Optional.of(entity(companyId, EntityType.SECURITY)));

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        assertEquals(OpenDartCompanyBootstrapStatus.INVALID_ENTITY_TYPE, result.status());
        verify(identifiers, never()).registerOrReuse(any());
    }

    @Test
    void missingEntityIsRejectedBeforeMapping() {
        when(entities.findById(companyId)).thenReturn(Optional.empty());

        var result = operation.execute(new OpenDartCompanyBootstrapCommand(companyId, "00126380"));

        assertEquals(OpenDartCompanyBootstrapStatus.ENTITY_NOT_FOUND, result.status());
        verify(identifiers, never()).registerOrReuse(any());
    }

    @Test
    void unknownCorpCodeIsNotFoundWithoutNameOrStockFallback() {
        var result = operation.execute(new OpenDartCompanyBootstrapCommand(companyId, "99999999"));

        assertEquals(OpenDartCompanyBootstrapStatus.NOT_FOUND, result.status());
        assertNull(result.officialRecord());
        verify(identifiers, never()).registerOrReuse(any());
    }

    @Test
    void leadingZeroIsPreservedInRegistryKey() throws Exception {
        var stored = identifier(UUID.randomUUID());
        when(identifiers.registerOrReuse(registration(companyId))).thenReturn(stored);

        operation.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        verify(identifiers).registerOrReuse(registration(companyId));
    }

    @Test
    void oneSessionFetchesDirectoryOnceAcrossDryRunAndApply() throws Exception {
        var stored = identifier(UUID.randomUUID());
        when(identifiers.registerOrReuse(registration(companyId))).thenReturn(stored);
        var session = operation.openSession();

        session.execute(new OpenDartCompanyBootstrapCommand(companyId, "00126380"));
        session.execute(new OpenDartCompanyBootstrapCommand(
                companyId, "00126380", OpenDartCompanyBootstrapMode.APPLY));

        verify(client, times(1)).fetch();
    }

    @Test
    void invalidInputIsRejectedBeforeDirectoryFetch() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartCompanyBootstrapCommand(companyId, "126380"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenDartCompanyBootstrapCommand(null, "00126380"));
        verify(client, never()).fetch();
    }

    private static ExternalIdentifierRegistration registration(UUID entityId) {
        return new ExternalIdentifierRegistration(entityId,
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", "00126380"));
    }

    private static MarketEntity entity(UUID id, EntityType type) throws Exception {
        var constructor = MarketEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var value = constructor.newInstance();
        set(value, "id", id);
        set(value, "entityType", type);
        return value;
    }

    private static EntityExternalIdentifier identifier(UUID id) throws Exception {
        var constructor = EntityExternalIdentifier.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var value = constructor.newInstance();
        set(value, "id", id);
        return value;
    }

    private static void set(Object target, String fieldName, Object fieldValue) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, fieldValue);
    }
}
