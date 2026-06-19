package com.urlshortener.exception;

/**
 * Thrown when a submitted URL fails validation (malformed, disallowed scheme,
 * over the length limit, or pointing at a private/internal address). Mapped to
 * HTTP 400 by the global exception handler.
 *
 * <p>Per the spec's SSRF guidance, the message is intentionally generic so the
 * response never reveals which specific check failed.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
