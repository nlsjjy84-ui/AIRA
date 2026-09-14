package com.aira.api.market.ecos;

public interface EcosMetadataClient {
    EcosRawResponse fetch(EcosRequestDescriptor request);
}
