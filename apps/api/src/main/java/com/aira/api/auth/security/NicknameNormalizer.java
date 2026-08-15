package com.aira.api.auth.security;

import com.aira.api.auth.config.AuthProperties;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public final class NicknameNormalizer {
    private static final Pattern ALLOWED =
            Pattern.compile("^[가-힣A-Za-z][가-힣A-Za-z0-9]{2,19}$");
    private final Set<String> reservedNicknames;

    public NicknameNormalizer(AuthProperties properties) {
        this.reservedNicknames = properties.getReservedNicknames().stream()
                .map(NicknameNormalizer::normalizeValue)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String normalize(String nickname) {
        String normalized = normalizeDisplay(nickname).toLowerCase(Locale.ROOT);
        validateShape(normalized);
        if (reservedNicknames.contains(normalized)) {
            throw new IllegalArgumentException("Nickname is reserved");
        }
        return normalized;
    }

    public String normalizeDisplay(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("Nickname must not be null");
        }
        String displayNickname = Normalizer.normalize(nickname, Normalizer.Form.NFKC);
        validateShape(displayNickname);
        return displayNickname;
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Reserved nickname must not be null");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static void validateShape(String nickname) {
        if (nickname == null || !ALLOWED.matcher(nickname).matches()) {
            throw new IllegalArgumentException("Nickname does not satisfy the required format");
        }
    }
}
