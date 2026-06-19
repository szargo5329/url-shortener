package com.urlshortener.exception;

/**
 * Thrown when a short code does not resolve to a stored mapping. Mapped to
 * HTTP 404 by the global exception handler.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
