package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EcosProviderApplicationErrorDecoderTests {
    private static final Map<String, EcosProviderException.Category> EXPECTED = Map.ofEntries(
            Map.entry("INFO-100", EcosProviderException.Category.AUTHENTICATION),
            Map.entry("INFO-200", EcosProviderException.Category.NO_DATA),
            Map.entry("ERROR-100", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-101", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-200", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-300", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-301", EcosProviderException.Category.INVALID_REQUEST),
            Map.entry("ERROR-400", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-500", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-600", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-601", EcosProviderException.Category.PROVIDER_FAILURE),
            Map.entry("ERROR-602", EcosProviderException.Category.RATE_LIMIT));

    @Test
    void mapsAllTwelveOfficialCodesExactly() {
        EXPECTED.forEach((code, category) -> {
            var error = assertThrows(EcosProviderException.class,
                    () -> EcosProviderApplicationErrorDecoder.throwIfPresent(raw(code, "official message")));
            assertEquals(category, error.category(), code);
            assertEquals(code, error.providerCode(), code);
            assertEquals("ECOS provider returned " + code, error.getMessage(), code);
        });
    }

    @Test
    void providerMessageIsValidatedButNeverCopiedToException() {
        String sentinel = "SECRET_SENTINEL_SHOULD_NOT_ESCAPE";
        var error = assertThrows(EcosProviderException.class,
                () -> EcosProviderApplicationErrorDecoder.throwIfPresent(
                        raw("INFO-100", sentinel)));

        assertEquals(EcosProviderException.Category.AUTHENTICATION, error.category());
        assertFalse(error.toString().contains(sentinel));
    }

    @Test
    void normalSuccessEnvelopeIsIgnoredByErrorDecoder() {
        assertDoesNotThrow(() -> EcosProviderApplicationErrorDecoder.throwIfPresent(
                new EcosRawResponse(200, "{\"StatisticSearch\":{\"list_total_count\":0,\"row\":[]}}")));
    }

    @Test
    void unknownOrMalformedResultEnvelopeIsBlocked() {
        assertMalformed("{\"RESULT\":{\"CODE\":\"ERROR-999\",\"MESSAGE\":\"unknown\"}}");
        assertMalformed("{\"RESULT\":{\"CODE\":\"INFO-200\"}}");
        assertMalformed("{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"x\",\"EXTRA\":1}}");
        assertMalformed("{\"RESULT\":{\"CODE\":200,\"MESSAGE\":\"x\"}}");
        assertMalformed("{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\" \"}}");
        assertMalformed("{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"x\"},\"extra\":{}}");
    }

    private static EcosRawResponse raw(String code, String message) {
        return new EcosRawResponse(200,
                "{\"RESULT\":{\"CODE\":\"" + code + "\",\"MESSAGE\":\"" + message + "\"}}");
    }

    private static void assertMalformed(String body) {
        var error = assertThrows(EcosProviderException.class,
                () -> EcosProviderApplicationErrorDecoder.throwIfPresent(new EcosRawResponse(200, body)));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
    }
}
