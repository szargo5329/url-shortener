package com.urlshortener.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.NotFoundException;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
@TestPropertySource(properties = "app.cors.allowed-origin=https://www.myapp.io")
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    @DisplayName("POST /shorten returns 201 with the response body on success")
    @SuppressWarnings("null") // MediaType.APPLICATION_JSON / nullValue() aren't @NonNull-annotated (false positive)
    void shortenReturnsCreated() throws Exception {
        ShortenResponse response = new ShortenResponse(
                "abc1234",
                "https://myapp.io/abc1234",
                "https://example.com/page",
                "2026-01-01T00:00:00Z",
                null);
        when(urlService.shorten("https://example.com/page")).thenReturn(response);

        String body = """
                {"long_url":"https://example.com/page"}
                """;

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").value("abc1234"))
                .andExpect(jsonPath("$.short_url").value("https://myapp.io/abc1234"))
                .andExpect(jsonPath("$.long_url").value("https://example.com/page"))
                .andExpect(jsonPath("$.created_at").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.expires_at").value(nullValue()));
    }

    @Test
    @DisplayName("POST /shorten returns 400 when long_url is blank")
    @SuppressWarnings("null") // MediaType.APPLICATION_JSON isn't @NonNull-annotated (false positive)
    void shortenReturnsBadRequestForBlankUrl() throws Exception {
        String body = """
                {"long_url":"   "}
                """;

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("long_url is required"));

        // Validation must short-circuit before the service is invoked.
        verify(urlService, never()).shorten(anyString());
    }

    @Test
    @DisplayName("GET /{code} returns 302 with the Location header on success")
    void redirectReturnsFound() throws Exception {
        when(urlService.redirect("abc1234")).thenReturn("https://example.com/page");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));
    }

    @Test
    @DisplayName("GET /{code} returns 404 when the short code is unknown")
    void redirectReturnsNotFound() throws Exception {
        when(urlService.redirect("missing"))
                .thenThrow(new NotFoundException("Short code not found"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Short code not found"));
    }
}
