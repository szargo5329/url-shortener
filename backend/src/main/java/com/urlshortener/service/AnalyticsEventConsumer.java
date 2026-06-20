package com.urlshortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.model.ClickEvent;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Consumes click-event messages from SQS and persists them to the
 * {@code click-events} DynamoDB table (Section 17.7).
 *
 * <p>This is the analytics Lambda's entry point: in production AWS invokes it via
 * the SQS event source mapping (one message body per call), not through Spring's
 * normal request handling. Implemented as a {@link Consumer} of the raw message
 * body so it fits the functional handler model.
 *
 * <p>Unlike the publish side (which is fire-and-forget), failures here propagate
 * so the message is not deleted and SQS can redeliver / route to a DLQ.
 */
@Service
public class AnalyticsEventConsumer implements Consumer<String> {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventConsumer.class);

    private final DynamoDbTable<ClickEvent> table;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticsEventConsumer(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.dynamodb.click-events-table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ClickEvent.class));
    }

    /**
     * Parses a click-event JSON payload and writes it to DynamoDB.
     *
     * @param messageBody the raw SQS message body ({@code short_code} +
     *                    {@code clicked_at} JSON)
     */
    @Override
    public void accept(String messageBody) {
        final JsonNode node;
        try {
            node = objectMapper.readTree(messageBody);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse click event message: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid click event payload", e);
        }

        String shortCode = node.path("short_code").asText();
        String clickedAt = node.path("clicked_at").asText();

        ClickEvent event = new ClickEvent(shortCode, clickedAt);
        table.putItem(event);
    }
}
