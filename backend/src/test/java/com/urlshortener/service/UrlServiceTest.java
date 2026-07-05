package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.NotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.CacheRepository;
import com.urlshortener.repository.DynamoDbRepository;
import com.urlshortener.util.ShortCodeGenerator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final String BASE_URL = "https://myapp.io";

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private DynamoDbRepository dynamoDbRepository;

    @Mock
    private CacheRepository cacheRepository;

    @Mock
    private AnalyticsService analyticsService;

    private UrlService service;

    @BeforeEach
    void setUp() {
        service = new UrlService(
                shortCodeGenerator, dynamoDbRepository, cacheRepository, analyticsService, BASE_URL);
    }

    // ---------------------------------------------------------------------
    // shorten() — happy path
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("shorten() persists a mapping and returns the populated response")
    void shortenReturnsResponseForValidUrl() {
        // Public IP literal keeps host validation offline (no DNS lookup).
        String longUrl = "https://8.8.8.8/some/page";
        when(shortCodeGenerator.generateUnique(any())).thenReturn("abc1234");

        ShortenResponse response = service.shorten(longUrl);

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo(BASE_URL + "/abc1234");
        assertThat(response.longUrl()).isEqualTo(longUrl);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.expiresAt()).isNull();

        ArgumentCaptor<UrlMapping> saved = ArgumentCaptor.forClass(UrlMapping.class);
        verify(dynamoDbRepository).save(saved.capture());
        assertThat(saved.getValue().getShortCode()).isEqualTo("abc1234");
        assertThat(saved.getValue().getLongUrl()).isEqualTo(longUrl);
        assertThat(saved.getValue().getExpiresAt()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isNotNull();

        // Shortening does not emit click events — only redirects do.
        verifyNoInteractions(analyticsService);
    }

    // ---------------------------------------------------------------------
    // shorten() — validation / SSRF (Section 16.2)
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "rejects \"{0}\"")
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "   ",                                  // blank
            "ftp://example.com/file",               // disallowed scheme
            "file:///etc/passwd",                   // disallowed scheme
            "javascript:alert(1)",                  // disallowed scheme
            "http://exa mple.com",                  // malformed (illegal space)
            "http://localhost:8080/admin",          // localhost literal
            "http://127.0.0.1/",                    // loopback (127.0.0.0/8)
            "http://10.0.0.1/",                     // private (10.0.0.0/8)
            "http://172.16.5.4/",                   // private (172.16.0.0/12)
            "http://192.168.1.1/",                  // private (192.168.0.0/16)
            "http://0.0.0.0/"                       // any-local
    })
    @DisplayName("shorten() rejects malformed, disallowed-scheme, and private/internal URLs")
    void shortenRejectsInvalidUrls(String badUrl) {
        assertThatThrownBy(() -> service.shorten(badUrl))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dynamoDbRepository);
    }

    @Test
    @DisplayName("shorten() rejects the AWS metadata endpoint 169.254.169.254 (SSRF)")
    void shortenRejectsAwsMetadataEndpoint() {
        assertThatThrownBy(() -> service.shorten("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dynamoDbRepository);
    }

    @Test
    @DisplayName("shorten() rejects a URL longer than the 2048-char limit")
    void shortenRejectsOversizedUrl() {
        // Length check runs before parsing/resolution, so this stays offline.
        String oversized = "https://8.8.8.8/" + "a".repeat(2048);

        assertThatThrownBy(() -> service.shorten(oversized))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(dynamoDbRepository);
    }

    // ---------------------------------------------------------------------
    // redirect() — cache-aside + fire-and-forget analytics (Section 17.9)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("redirect() returns from cache and skips DynamoDB on a cache hit")
    void redirectCacheHitSkipsDynamoDb() {
        when(cacheRepository.get("abc1234")).thenReturn(Optional.of("https://example.com/page"));

        String result = service.redirect("abc1234");

        assertThat(result).isEqualTo("https://example.com/page");
        verifyNoInteractions(dynamoDbRepository);
        verify(cacheRepository, never()).put(any(), any());
        verify(analyticsService).publishClickEvent("abc1234");
    }

    @Test
    @DisplayName("redirect() falls back to DynamoDB and repopulates the cache on a miss")
    void redirectCacheMissFallsBackToDynamoDb() {
        UrlMapping mapping =
                new UrlMapping("abc1234", "https://example.com/page", "2026-01-01T00:00:00Z", null);
        when(cacheRepository.get("abc1234")).thenReturn(Optional.empty());
        when(dynamoDbRepository.getByShortCode("abc1234")).thenReturn(Optional.of(mapping));

        String result = service.redirect("abc1234");

        assertThat(result).isEqualTo("https://example.com/page");
        verify(cacheRepository).put("abc1234", "https://example.com/page");
        verify(analyticsService).publishClickEvent("abc1234");
    }

    @Test
    @DisplayName("redirect() throws NotFoundException and emits no click event when the code is unknown")
    void redirectThrowsWhenNotFound() {
        when(cacheRepository.get("missing")).thenReturn(Optional.empty());
        when(dynamoDbRepository.getByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redirect("missing"))
                .isInstanceOf(NotFoundException.class);

        verify(analyticsService, never()).publishClickEvent(any());
    }
}
