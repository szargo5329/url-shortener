package com.urlshortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes click events to SQS for the analytics pipeline (Section 17).
 *
 * <p><b>Fire-and-forget:</b> {@link #publishClickEvent(String)} never throws
 * back to the caller. A failed analytics publish must never degrade the core
 * redirect — any error is logged and swallowed, per Section 17.9.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticsService(
            SqsClient sqsClient,
            @Value("${app.sqs.queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    /**
     * Publishes a minimal click event ({@code short_code} + ISO-8601
     * {@code clicked_at}) to SQS. Best-effort and non-blocking: never throws.
     *
     * @param shortCode the short code that was clicked
     */
    public void publishClickEvent(String shortCode) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("short_code", shortCode);
            event.put("clicked_at", Instant.now().toString());
            String payload = objectMapper.writeValueAsString(event);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish click event for {}: {}", shortCode, e.getMessage());
            // Intentionally swallowed — analytics failure must not break redirect.
        }
    }
}
