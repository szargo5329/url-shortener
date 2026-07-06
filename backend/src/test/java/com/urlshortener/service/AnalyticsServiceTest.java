package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/123456789012/click-events";

    @Mock
    private SqsClient sqsClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(sqsClient, QUEUE_URL);
    }

    @Test
    @DisplayName("publishClickEvent() sends a message to the configured queue with short_code and clicked_at")
    void publishSendsMessageWithCorrectQueueAndBody() throws Exception {
        service.publishClickEvent("abc1234");

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());

        SendMessageRequest request = captor.getValue();
        assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);

        JsonNode body = objectMapper.readTree(request.messageBody());
        assertThat(body.path("short_code").asText()).isEqualTo("abc1234");
        // clicked_at must be present and a valid ISO-8601 instant.
        assertThat(body.path("clicked_at").asText()).isNotBlank();
        assertThat(Instant.parse(body.path("clicked_at").asText())).isNotNull();
    }

    @Test
    @DisplayName("publishClickEvent() swallows SQS failures (fire-and-forget, Section 17.9)")
    void publishSwallowsSqsFailure() {
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS unavailable"));

        // The fire-and-forget contract: a publish failure must never propagate.
        assertThatCode(() -> service.publishClickEvent("abc1234"))
                .doesNotThrowAnyException();

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }
}
