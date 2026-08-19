package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.repository.SourceRegistrationStore;
import com.aira.api.market.repository.SourceRepository;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceRegistryServiceTests {
    @Mock private SourceRegistrationStore registrations;
    @Mock private SourceRepository sources;

    private SourceRegistryService service;

    @BeforeEach
    void setUp() {
        service = new SourceRegistryService(registrations, sources);
    }

    @Test
    void registersFirstSourceAndReturnsPersistedEntity() throws Exception {
        UUID id = UUID.randomUUID();
        Source source = newSource();
        SourceRegistration registration = new SourceRegistration(
                SourceType.REGULATOR, "sec-edgar", "SEC EDGAR", "SEC.GOV");
        when(registrations.registerOrGetId(registration)).thenReturn(id);
        when(sources.findById(id)).thenReturn(Optional.of(source));

        assertSame(source, service.registerOrReuse(registration));
        assertEquals("sec.gov", registration.canonicalDomain());
    }

    @Test
    void repeatedRegistrationReusesDatabaseIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        Source source = newSource();
        SourceRegistration registration = new SourceRegistration(
                SourceType.EXCHANGE, "nasdaq", "Nasdaq", "nasdaq.com");
        when(registrations.registerOrGetId(registration)).thenReturn(id);
        when(sources.findById(id)).thenReturn(Optional.of(source));

        assertSame(source, service.registerOrReuse(registration));
        assertSame(source, service.registerOrReuse(registration));
        verify(registrations, org.mockito.Mockito.times(2)).registerOrGetId(registration);
    }

    @Test
    void differentExternalKeysRemainDifferentIdentities() throws Exception {
        Source first = newSource();
        Source second = newSource();
        SourceRegistration one = new SourceRegistration(SourceType.NEWS, "one", "One", null);
        SourceRegistration two = new SourceRegistration(SourceType.NEWS, "two", "Two", null);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(registrations.registerOrGetId(one)).thenReturn(firstId);
        when(registrations.registerOrGetId(two)).thenReturn(secondId);
        when(sources.findById(firstId)).thenReturn(Optional.of(first));
        when(sources.findById(secondId)).thenReturn(Optional.of(second));

        assertSame(first, service.registerOrReuse(one));
        assertSame(second, service.registerOrReuse(two));
    }

    @Test
    void findsSourceByStableIdentity() throws Exception {
        Source source = newSource();
        when(sources.findBySourceTypeAndExternalKey(SourceType.COMPANY_IR, "aapl-ir"))
                .thenReturn(Optional.of(source));

        assertSame(source, service.find(SourceType.COMPANY_IR, " aapl-ir ").orElseThrow());
    }

    @Test
    void rejectsMissingOrMalformedIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new SourceRegistration(null, "key", "Name", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceRegistration(SourceType.OTHER, " ", "Name", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceRegistration(SourceType.OTHER, "key", " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceRegistration(SourceType.OTHER, "key", "Name", "https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> service.registerOrReuse(null));
    }

    @Test
    void reportsMissingPersistedSourceAsInvariantFailure() {
        SourceRegistration registration = new SourceRegistration(
                SourceType.GOVERNMENT, "bls", "BLS", "bls.gov");
        UUID id = UUID.randomUUID();
        when(registrations.registerOrGetId(registration)).thenReturn(id);
        when(sources.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.registerOrReuse(registration));
    }

    private static Source newSource() throws Exception {
        Constructor<Source> constructor = Source.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
