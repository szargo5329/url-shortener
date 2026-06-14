package com.urlshortener.util;

import java.security.SecureRandom;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Generates short codes for URL mappings.
 *
 * <p>A code is {@value #CODE_LENGTH} characters drawn uniformly from a 62-char
 * Base62 alphabet ({@code [a-zA-Z0-9]}), giving 62^7 ≈ 3.5 trillion possible
 * codes — collision-proof at any realistic scale for this project. Codes are
 * produced with {@link SecureRandom} so they are not guessable (unlike
 * sequential IDs) and stay compact (unlike UUIDs).
 *
 * <p>Per the spec's generation strategy, callers persist into a keyspace where
 * a code must be unique. Raw generation cannot guarantee that on its own, so
 * {@link #generateUnique(Predicate)} wraps {@link #generate()} in a
 * generate-check-retry loop; the caller supplies the existence check (e.g. a
 * DynamoDB lookup). This keeps the generator free of any storage dependency.
 */
@Component
public class ShortCodeGenerator {

    /** Number of characters in a generated short code. */
    public static final int CODE_LENGTH = 7;

    /** Base62 alphabet: digits, uppercase, then lowercase. */
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /**
     * Upper bound on retries before giving up. Collisions are astronomically
     * unlikely at this scale, so exhausting this many attempts indicates a real
     * problem (e.g. a broken existence check) rather than bad luck.
     */
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a single random {@value #CODE_LENGTH}-character Base62 code.
     * No uniqueness guarantee — use {@link #generateUnique(Predicate)} when the
     * code must not already exist.
     *
     * @return a freshly generated short code
     */
    public String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }

    /**
     * Generates a short code that does not already exist, retrying on collision.
     *
     * @param exists predicate returning {@code true} if a candidate code is
     *               already taken (e.g. a DynamoDB existence check)
     * @return a short code for which {@code exists} returned {@code false}
     * @throws IllegalStateException if a free code is not found within
     *                               {@value #MAX_ATTEMPTS} attempts
     */
    public String generateUnique(Predicate<String> exists) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique short code after " + MAX_ATTEMPTS + " attempts");
    }
}
