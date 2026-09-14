package com.aira.api.market.ecos;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.service.SourceRegistration;

final class EcosOfficialSourceContract {
    static final SourceRegistration BOK_ECOS = new SourceRegistration(
            SourceType.GOVERNMENT,
            "BOK_ECOS",
            "Bank of Korea ECOS",
            "ecos.bok.or.kr");

    private EcosOfficialSourceContract() {}

    static void requireCanonicalBokEcos(Source source) {
        if (source == null
                || source.getId() == null
                || source.getSourceType() != BOK_ECOS.sourceType()
                || !BOK_ECOS.externalKey().equals(source.getExternalKey())
                || !BOK_ECOS.name().equals(source.getName())
                || !BOK_ECOS.canonicalDomain().equals(source.getCanonicalDomain())
                || !source.isActive()) {
            throw new IllegalStateException(
                    "BOK_ECOS source conflicts with AIRA Source Registry v1");
        }
    }
}
