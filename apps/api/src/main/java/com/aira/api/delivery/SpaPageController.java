package com.aira.api.delivery;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaPageController {
    @GetMapping({"/explore", "/finance", "/privacy", "/stock/{companyId}", "/screen", "/market", "/study", "/plans", "/classes", "/status", "/check"})
    public String forwardSpaRoute() {
        return "forward:/index.html";
    }
}
