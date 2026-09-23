package kz.hackalem.wamigos.meeting.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import kz.hackalem.wamigos.error.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenManager {

    private static final int TOKEN_BYTES = 32;
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] value = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String extractAndHash(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new NotFoundException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || token.contains(" ")) {
            throw new NotFoundException();
        }
        return hash(token);
    }

    public boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            value.append(Character.forDigit(current & 0x0f, 16));
        }
        return value.toString();
    }
}
