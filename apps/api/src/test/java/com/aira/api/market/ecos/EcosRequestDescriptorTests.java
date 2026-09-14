package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EcosRequestDescriptorTests {
    @Test void fixesOperationAndAcceptsValidDescriptor() {
        var descriptor = new EcosRequestDescriptor(1, 1000, "candidate-series");
        assertEquals("StatisticItemList", descriptor.operation());
    }

    @Test void rejectsBlankOrNullStatCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRequestDescriptor(1, 1000, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRequestDescriptor(1, 1000, null));
    }

    @Test void rejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRequestDescriptor(0, 1000, "candidate-series"));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRequestDescriptor(2, 1, "candidate-series"));
    }

    @Test void exposesNoCredentialOrRawUrlFields() {
        var fieldNames = Arrays.stream(EcosRequestDescriptor.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .toList();
        assertFalse(fieldNames.stream().anyMatch(name ->
                name.contains("key") || name.contains("url") ||
                        name.contains("uri") || name.contains("auth")));
    }
}
