package com.aira.api.market.controller;

import com.aira.api.market.dto.PublicEventResponse;
import com.aira.api.market.dto.PublicEventsResponse;
import com.aira.api.market.query.PublicEventFeedQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class PublicEventController {
    private final PublicEventFeedQuery query;

    public PublicEventController(PublicEventFeedQuery query) {
        this.query = query;
    }

    @GetMapping
    public PublicEventsResponse findRecentEvents() {
        return new PublicEventsResponse(query.findRecentEvents().stream()
                .map(event -> new PublicEventResponse(event.eventId(), event.companyId(),
                        event.companyName(), event.eventType(), event.title(), event.occurredAt()))
                .toList());
    }
}
