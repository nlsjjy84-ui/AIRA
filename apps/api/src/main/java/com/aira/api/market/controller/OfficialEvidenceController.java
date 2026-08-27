package com.aira.api.market.controller;

import com.aira.api.market.dto.OfficialEvidenceResponse;
import com.aira.api.market.query.OfficialEvidenceNotFoundException;
import com.aira.api.market.query.OfficialEvidenceQuery;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evidence/{evidenceId}")
public class OfficialEvidenceController {
    private final OfficialEvidenceQuery query;

    public OfficialEvidenceController(OfficialEvidenceQuery query) {
        this.query = query;
    }

    @GetMapping
    public OfficialEvidenceResponse find(@PathVariable UUID evidenceId) {
        return query.find(evidenceId);
    }

    @ExceptionHandler(OfficialEvidenceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}
}
