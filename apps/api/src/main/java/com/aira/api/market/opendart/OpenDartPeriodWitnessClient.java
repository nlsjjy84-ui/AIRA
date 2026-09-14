package com.aira.api.market.opendart;

public interface OpenDartPeriodWitnessClient {
    OpenDartPeriodWitnessResponse fetch(String corpCode, int businessYear);
}
