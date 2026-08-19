package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenDartCompanyIdentifierBootstrapTests {
    @Mock private OpenDartCompanyDirectoryClient client;
    @Mock private EntityExternalIdentifierRegistryService identifiers;
    @Mock private EntityExternalIdentifier identifier;

    private OpenDartCompanyIdentifierBootstrap bootstrap;
    private OpenDartCompanyDirectoryRecord record;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        bootstrap = new OpenDartCompanyIdentifierBootstrap(client, identifiers);
        record = new OpenDartCompanyDirectoryRecord("00126380", "삼성전자(주)",
                "SAMSUNG ELECTRONICS CO.,LTD.", "005930", LocalDate.of(2025, 8, 29));
        companyId = UUID.randomUUID();
    }

    @Test
    void explicitlyRegistersVerifiedCorpCodeForCallerSuppliedEntity() {
        when(client.fetch()).thenReturn(new OpenDartCompanyDirectory(List.of(record)));
        var registration = new ExternalIdentifierRegistration(companyId,
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", "00126380"));
        when(identifiers.registerOrReuse(registration)).thenReturn(identifier);

        var result = bootstrap.register(companyId, "00126380");

        assertSame(record, result.officialRecord());
        assertSame(identifier, result.identifier());
        verify(identifiers).registerOrReuse(registration);
    }

    @Test
    void rejectsUnknownCorpCodeWithoutCreatingOrMappingAnything() {
        when(client.fetch()).thenReturn(new OpenDartCompanyDirectory(List.of(record)));

        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.register(companyId, "99999999"));
        verify(identifiers, never()).registerOrReuse(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delegatesSecurityRejectionToExistingRegistryPolicy() {
        when(client.fetch()).thenReturn(new OpenDartCompanyDirectory(List.of(record)));
        var registration = new ExternalIdentifierRegistration(companyId,
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", "00126380"));
        when(identifiers.registerOrReuse(registration))
                .thenThrow(new IllegalArgumentException(
                        "OpenDART corp code can only identify a company entity"));

        var failure = assertThrows(IllegalArgumentException.class,
                () -> bootstrap.register(companyId, "00126380"));
        assertEquals("OpenDART corp code can only identify a company entity", failure.getMessage());
    }

    @Test
    void preservesExistingRemappingConflictWithoutUpdate() {
        when(client.fetch()).thenReturn(new OpenDartCompanyDirectory(List.of(record)));
        var registration = new ExternalIdentifierRegistration(companyId,
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", "00126380"));
        var conflict = new ExternalIdentifierConflictException();
        when(identifiers.registerOrReuse(registration)).thenThrow(conflict);

        assertSame(conflict, assertThrows(ExternalIdentifierConflictException.class,
                () -> bootstrap.register(companyId, "00126380")));
    }

    @Test
    void rejectsNullEntityAndInvalidCorpCodeBeforeProviderCall() {
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.register(null, "00126380"));
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.register(companyId, "126380"));
        verify(client, never()).fetch();
    }
}
