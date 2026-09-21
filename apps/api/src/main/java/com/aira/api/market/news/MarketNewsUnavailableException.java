package com.aira.api.market.news;

public class MarketNewsUnavailableException extends RuntimeException {
    public MarketNewsUnavailableException(Throwable cause) { super("Recent market news is unavailable", cause); }
}
