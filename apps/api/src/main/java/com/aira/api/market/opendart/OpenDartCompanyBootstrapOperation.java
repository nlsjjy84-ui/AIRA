package com.aira.api.market.opendart;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class OpenDartCompanyBootstrapOperation {
    private final OpenDartCompanyDirectoryClient directoryClient;
    private final MarketEntityRepository entities;
    private final EntityExternalIdentifierRegistryService identifiers;

    public OpenDartCompanyBootstrapOperation(OpenDartCompanyDirectoryClient directoryClient,
            MarketEntityRepository entities,
            EntityExternalIdentifierRegistryService identifiers) {
        this.directoryClient = directoryClient;
        this.entities = entities;
        this.identifiers = identifiers;
    }

    public Session openSession() {
        return new Session(directoryClient.fetch(), entities, identifiers);
    }

    public OpenDartCompanyBootstrapResult execute(OpenDartCompanyBootstrapCommand command) {
        return openSession().execute(command);
    }

    public static final class Session {
        private final OpenDartCompanyDirectory directory;
        private final MarketEntityRepository entities;
        private final EntityExternalIdentifierRegistryService identifiers;

        private Session(OpenDartCompanyDirectory directory,
                MarketEntityRepository entities,
                EntityExternalIdentifierRegistryService identifiers) {
            this.directory = directory;
            this.entities = entities;
            this.identifiers = identifiers;
        }

        public OpenDartCompanyBootstrapResult execute(OpenDartCompanyBootstrapCommand command) {
            if (command == null) {
                throw new IllegalArgumentException("Bootstrap command is required");
            }
            var entity = entities.findById(command.entityId());
            if (entity.isEmpty()) {
                return result(OpenDartCompanyBootstrapStatus.ENTITY_NOT_FOUND, command, null,
                        null, false);
            }
            if (entity.orElseThrow().getEntityType() != EntityType.COMPANY) {
                return result(OpenDartCompanyBootstrapStatus.INVALID_ENTITY_TYPE, command, null,
                        null, false);
            }
            var record = directory.findByCorpCode(command.corpCode());
            if (record.isEmpty()) {
                return result(OpenDartCompanyBootstrapStatus.NOT_FOUND, command, null,
                        null, false);
            }

            var key = new ExternalIdentifierKey("OPENDART", "CORP_CODE", command.corpCode());
            var mappedEntity = identifiers.findEntity(key);
            boolean sameMapping = mappedEntity
                    .map(mapped -> mapped.getId().equals(command.entityId()))
                    .orElse(false);
            if (mappedEntity.isPresent() && !sameMapping) {
                return result(OpenDartCompanyBootstrapStatus.CONFLICT, command,
                        record.orElseThrow(), null, true);
            }
            if (command.mode() == OpenDartCompanyBootstrapMode.DRY_RUN) {
                return result(OpenDartCompanyBootstrapStatus.VALID_DRY_RUN, command,
                        record.orElseThrow(), null, sameMapping);
            }

            try {
                var stored = identifiers.registerOrReuse(
                        new ExternalIdentifierRegistration(command.entityId(), key));
                return result(sameMapping ? OpenDartCompanyBootstrapStatus.REUSED
                                : OpenDartCompanyBootstrapStatus.REGISTERED,
                        command, record.orElseThrow(), stored.getId(), sameMapping);
            } catch (ExternalIdentifierConflictException conflict) {
                return result(OpenDartCompanyBootstrapStatus.CONFLICT, command,
                        record.orElseThrow(), null, true);
            }
        }

        private static OpenDartCompanyBootstrapResult result(
                OpenDartCompanyBootstrapStatus status,
                OpenDartCompanyBootstrapCommand command,
                OpenDartCompanyDirectoryRecord record,
                UUID identifierId,
                boolean existingMapping) {
            return new OpenDartCompanyBootstrapResult(status, command.entityId(),
                    command.corpCode(), record, identifierId, existingMapping);
        }
    }
}
