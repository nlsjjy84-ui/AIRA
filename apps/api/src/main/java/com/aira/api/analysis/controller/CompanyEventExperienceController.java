package com.aira.api.analysis.controller;

import com.aira.api.analysis.dto.CompanyEventExperienceResponse;
import com.aira.api.analysis.query.CompanyEventExperienceQuery;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/events")
public class CompanyEventExperienceController {
    private final CompanyEventExperienceQuery query;
    public CompanyEventExperienceController(CompanyEventExperienceQuery query) { this.query = query; }
    @GetMapping
    public CompanyEventExperienceResponse find(@org.springframework.web.bind.annotation.PathVariable UUID companyId) {
        return query.find(companyId);
    }
}
