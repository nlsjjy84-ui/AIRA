package com.aira.api.market.opendart;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenDartDs005Ingestion {
    public record Result(List<UUID> events, List<String> rejectedReceipts) {}
    private final OpenDartDs005Client client;
    private final OpenDartDs005Parser parser;
    private final OpenDartMaterialEventRegistration registration;
    public OpenDartDs005Ingestion(OpenDartDs005Client client, OpenDartDs005Parser parser,
            OpenDartMaterialEventRegistration registration) {
        this.client = client; this.parser = parser; this.registration = registration;
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result ingest(OpenDartDs005Request request) {
        // HTTP and full-response validation finish outside persistence; P6 owns each receipt transaction.
        String body = client.fetch(request);
        OpenDartDs005Parser.Result parsed = parser.parse(request, body, OffsetDateTime.now(ZoneOffset.UTC));
        List<UUID> ids = new ArrayList<>();
        for (ValidatedMaterialEvent receipt : parsed.receipts()) ids.add(registration.register(receipt));
        return new Result(List.copyOf(ids), parsed.rejectedReceipts());
    }
}
