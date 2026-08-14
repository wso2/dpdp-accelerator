package com.openfgc.nomineeservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The shared key protecting the internal gate endpoints, which are called by
 * infrastructure rather than by a signed-in user and so carry no user token.
 *
 * <p>Comparison is time-independent: a byte-by-byte comparison returns sooner for
 * a key that shares a longer prefix with the real one, which leaks the key to a
 * caller able to measure it.
 */
@Component
public class InternalApiKey {

    private final byte[] expected;

    public InternalApiKey(@Value("${impersonation-gate.internal-api-key}") String configuredKey) {
        this.expected = configuredKey == null || configuredKey.isBlank()
                ? null
                : configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Whether the presented key matches. An unconfigured key matches nothing, so
     * a missing configuration closes the gate rather than opening it.
     */
    public boolean matches(String presented) {
        if (expected == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}
