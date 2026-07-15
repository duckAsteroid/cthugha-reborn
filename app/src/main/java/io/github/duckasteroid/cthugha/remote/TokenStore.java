package io.github.duckasteroid.cthugha.remote;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenStore {
    private volatile String currentToken;
    private final SecureRandom rng = new SecureRandom();
    private final String fixedToken;

    public TokenStore() {
        this(null);
    }

    /**
     * @param fixedToken when non-null, {@link #rotate} always (re)sets the token to this
     *                   value instead of generating a random one — for local development,
     *                   so the same URL/token can be reused across app restarts.
     */
    public TokenStore(String fixedToken) {
        this.fixedToken = fixedToken;
        if (fixedToken != null) {
            currentToken = fixedToken;
        }
    }

    public String rotate(String baseUrl) {
        if (fixedToken != null) {
            currentToken = fixedToken;
        } else {
            byte[] bytes = new byte[32]; // 256 bits
            rng.nextBytes(bytes);
            currentToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
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
