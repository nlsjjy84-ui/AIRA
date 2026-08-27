package com.aira.api.market.controller;

import com.aira.api.market.dto.PublicEventDetailResponse;
import com.aira.api.market.query.PublicEventDetailQuery;
import com.aira.api.market.query.PublicEventNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
public class PublicEventDetailController {
    private final PublicEventDetailQuery query;

    public PublicEventDetailController(PublicEventDetailQuery query) {
        this.query = query;
    }

    @GetMapping
    public PublicEventDetailResponse find(@PathVariable UUID eventId) {
        return query.find(eventId);
    }

    @ExceptionHandler(PublicEventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}
}
