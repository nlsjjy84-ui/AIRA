package com.aira.api.analysis.controller;

import com.aira.api.analysis.dto.HistoricalAssessmentResponse;
import com.aira.api.analysis.query.HistoricalAssessmentNotFoundException;
import com.aira.api.analysis.query.HistoricalAssessmentQuery;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assessments/{assessmentId}")
public class HistoricalAssessmentController {
    private final HistoricalAssessmentQuery query;

    public HistoricalAssessmentController(HistoricalAssessmentQuery query) {
        this.query = query;
    }

    @GetMapping
    public HistoricalAssessmentResponse find(@PathVariable UUID assessmentId) {
        return query.find(assessmentId);
    }

    @ExceptionHandler(HistoricalAssessmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}
}
