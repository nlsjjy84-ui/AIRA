package com.aira.api.assist;

import java.util.Map;

/** AI 조건 만들기 결과. filters 키는 화면의 조건 칸(capMin·opMin·roeMin·debtMax·growthMin·perMax·pbrMax)과 같다. */
public record ScreenResponse(String method, boolean understood, Map<String, String> filters, String market, String industry, String note) {
    static ScreenResponse notUnderstood(String note) {
        return new ScreenResponse("NONE", false, Map.of(), "ALL", "ALL", note);
    }
}
