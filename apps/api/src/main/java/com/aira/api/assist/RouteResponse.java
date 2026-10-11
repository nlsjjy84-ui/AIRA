package com.aira.api.assist;

import java.util.List;

/** A whitelisted navigation request. understood=false means the screen should run a plain search for entityQuery. */
public record RouteResponse(String method, boolean understood, String entityQuery, String perspective,
        String category, Integer year, List<String> items, String note) {
    static RouteResponse notUnderstood(String question, String note) {
        return new RouteResponse("NONE", false, question, null, null, null, List.of(), note);
    }
}
