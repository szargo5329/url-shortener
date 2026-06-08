package com.urlshortener.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /shorten}.
 *
 * <p>Basic presence/length validation is declared here; deeper checks
 * (scheme allow-list, SSRF private-IP blocking, URL parsing) live in the
 * service layer per the validation order in the spec.
 */
public record ShortenRequest(
        @JsonProperty("long_url")
        @NotBlank(message = "long_url is required")
        @Size(max = 2048, message = "Invalid URL")
        String longUrl
) {
}
