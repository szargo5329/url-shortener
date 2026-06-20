package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.urlshortener.model.UrlMapping;

/**
 * Response body for {@code POST /shorten} (HTTP 201).
 *
 * <p>{@code expires_at} is serialized even when {@code null} so the field is
 * always present in the contract, matching the example in the spec.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ShortenResponse(
        @JsonProperty("short_code") String shortCode,
        @JsonProperty("short_url") String shortUrl,
        @JsonProperty("long_url") String longUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("expires_at") String expiresAt
) {

    /** Builds a response from a persisted mapping plus the configured base URL. */
    public static ShortenResponse from(UrlMapping mapping, String baseShortUrl) {
        String shortUrl = baseShortUrl + "/" + mapping.getShortCode();
        return new ShortenResponse(
                mapping.getShortCode(),
                shortUrl,
                mapping.getLongUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt()
        );
    }
}
