package com.aira.api.market.opendart;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class OpenDartCompanyIdentifierBootstrap {
    private final OpenDartCompanyDirectoryClient directoryClient;
    private final EntityExternalIdentifierRegistryService identifiers;

    public OpenDartCompanyIdentifierBootstrap(OpenDartCompanyDirectoryClient directoryClient,
            EntityExternalIdentifierRegistryService identifiers) {
        this.directoryClient = directoryClient;
        this.identifiers = identifiers;
    }

    public Result register(UUID companyEntityId, String corpCode) {
        if (companyEntityId == null) {
            throw new IllegalArgumentException("Company entity identifier is required");
        }
        var key = new ExternalIdentifierKey("OPENDART", "CORP_CODE", corpCode);
        var officialRecord = directoryClient.fetch().findByCorpCode(key.identifierValue())
                .orElseThrow(() -> new IllegalArgumentException(
                        "OpenDART corp code was not found in the official directory"));
        EntityExternalIdentifier identifier = identifiers.registerOrReuse(
                new ExternalIdentifierRegistration(companyEntityId, key));
        return new Result(officialRecord, identifier);
    }

    public record Result(
            OpenDartCompanyDirectoryRecord officialRecord,
            EntityExternalIdentifier identifier) {
        public Result {
            if (officialRecord == null || identifier == null) {
                throw new IllegalArgumentException("Bootstrap result is incomplete");
            }
        }
    }
}
