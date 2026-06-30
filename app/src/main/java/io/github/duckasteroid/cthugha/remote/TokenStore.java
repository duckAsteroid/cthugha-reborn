package io.github.duckasteroid.cthugha.remote;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenStore {
    private volatile String currentToken;
    private final SecureRandom rng = new SecureRandom();

    public String rotate(String baseUrl) {
        byte[] bytes = new byte[32]; // 256 bits
        rng.nextBytes(bytes);
        currentToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return baseUrl + "?token=" + currentToken;
    }

    public boolean validate(String token) {
        String current = currentToken;
        if (current == null || token == null) return false;
        return MessageDigest.isEqual(current.getBytes(), token.getBytes());
    }

    public String getCurrent() {
        return currentToken;
    }
}
