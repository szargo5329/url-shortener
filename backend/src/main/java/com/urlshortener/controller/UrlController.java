package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the URL shortener.
 *
 * <ul>
 *   <li>{@code POST /shorten} — create a short code for a long URL (201).</li>
 *   <li>{@code GET /{code}} — resolve a short code to a 302 redirect.</li>
 * </ul>
 *
 * <p>CORS is restricted to the single configured frontend origin (never a
 * wildcard) per the security spec; the origin is supplied from configuration
 * ({@code FRONTEND_ORIGIN}) rather than hardcoded. Spring answers the OPTIONS
 * preflight automatically based on this configuration.
 */
@RestController
@CrossOrigin(
        origins = "${app.cors.allowed-origin}",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS},
        allowedHeaders = "Content-Type",
        maxAge = 86400)
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Creates a short code for the supplied long URL.
     *
     * @param request the validated request body containing {@code long_url}
     * @return 201 Created with the created mapping
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shorten(request.longUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Resolves a short code and redirects to the original URL.
     *
     * <p>Uses 302 Found (not 301) so every click reaches the server, keeping the
     * redirect un-cached by the browser for future analytics.
     *
     * @param code the short code from the path
     * @return 302 Found with the {@code Location} header set to the long URL
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        String longUrl = urlService.redirect(code);
        URI location = Objects.requireNonNull(URI.create(longUrl));
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
