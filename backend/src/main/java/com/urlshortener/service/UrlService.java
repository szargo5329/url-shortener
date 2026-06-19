package com.urlshortener.service;

import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.NotFoundException;
import com.urlshortener.model.ShortenResponse;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.CacheRepository;
import com.urlshortener.repository.DynamoDbRepository;
import com.urlshortener.util.ShortCodeGenerator;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core business logic for the URL shortener: creating short codes and resolving
 * them back to their original URLs.
 *
 * <p><b>Shorten</b> validates the URL (including SSRF protection per the spec),
 * generates a collision-free short code, persists the mapping to DynamoDB, and
 * returns the API response.
 *
 * <p><b>Redirect</b> follows the cache-aside pattern: read Redis first, fall
 * back to DynamoDB on a miss and repopulate the cache, and treat a missing code
 * as {@link NotFoundException} (HTTP 404).
 */
@Service
public class UrlService {

    /** Maximum accepted URL length, per the security spec. */
    private static final int MAX_URL_LENGTH = 2048;

    private final ShortCodeGenerator shortCodeGenerator;
    private final DynamoDbRepository dynamoDbRepository;
    private final CacheRepository cacheRepository;
    private final String baseShortUrl;

    public UrlService(
            ShortCodeGenerator shortCodeGenerator,
            DynamoDbRepository dynamoDbRepository,
            CacheRepository cacheRepository,
            @Value("${app.short-url.base}") String baseShortUrl) {
        this.shortCodeGenerator = shortCodeGenerator;
        this.dynamoDbRepository = dynamoDbRepository;
        this.cacheRepository = cacheRepository;
        this.baseShortUrl = baseShortUrl;
    }

    /**
     * Validates a long URL, assigns it a unique short code, persists the
     * mapping, and returns the response payload.
     *
     * @param longUrl the URL to shorten
     * @return the created mapping as a {@link ShortenResponse}
     * @throws InvalidUrlException if the URL fails any validation check
     */
    public ShortenResponse shorten(String longUrl) {
        validateUrl(longUrl);

        String shortCode = shortCodeGenerator.generateUnique(dynamoDbRepository::exists);
        String createdAt = Instant.now().toString();
        UrlMapping mapping = new UrlMapping(shortCode, longUrl, createdAt, null);

        dynamoDbRepository.save(mapping);

        return ShortenResponse.from(mapping, baseShortUrl);
    }

    /**
     * Resolves a short code to its original URL using cache-aside lookup.
     *
     * @param shortCode the short code to resolve
     * @return the original long URL
     * @throws NotFoundException if the short code does not exist
     */
    public String redirect(String shortCode) {
        String code = Objects.requireNonNull(shortCode, "shortCode must not be null");

        Optional<String> cached = cacheRepository.get(code);
        if (cached.isPresent()) {
            return cached.get();
        }

        UrlMapping mapping = dynamoDbRepository.getByShortCode(code)
                .orElseThrow(() -> new NotFoundException("Short code not found"));

        String longUrl = mapping.getLongUrl();
        cacheRepository.put(code, longUrl);
        return longUrl;
    }

    /**
     * Validates a candidate URL in the order defined by the security spec.
     * Every failure throws {@link InvalidUrlException} with the same generic
     * message so the response never reveals which check failed.
     */
    private void validateUrl(String longUrl) {
        // 1. Null / empty
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidUrlException("Invalid URL");
        }

        // 2. Length limit
        if (longUrl.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("Invalid URL");
        }

        // 3 & 4. Parse and verify scheme is http/https
        final URI uri;
        try {
            uri = new URI(longUrl);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Invalid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("Invalid URL");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("Invalid URL");
        }

        // Block obvious localhost literal before any resolution.
        if ("localhost".equals(host.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("Invalid URL");
        }

        // 5. Resolve the host and reject private / internal / loopback ranges.
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("Invalid URL");
        }

        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new InvalidUrlException("Invalid URL");
            }
        }
    }

    /**
     * Returns whether an address falls in a range that must never be reachable
     * via a shortened link (SSRF protection):
     * loopback (127.0.0.0/8, ::1), any-local (0.0.0.0),
     * link-local (169.254.0.0/16 — the AWS metadata endpoint — and fe80::/10),
     * and site-local (10/8, 172.16/12, 192.168/16).
     */
    private boolean isBlockedAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress();
    }
}
