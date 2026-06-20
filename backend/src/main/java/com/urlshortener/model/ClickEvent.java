package com.urlshortener.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB entity for the {@code click-events} table (Section 17.4).
 *
 * <p>Uses a composite key: {@code short_code} (partition) plus {@code clicked_at}
 * (sort, ISO-8601 timestamp), which lets the V2 dashboard query all clicks for a
 * given short code ordered by time.
 *
 * <p>The DynamoDB enhanced client requires a public no-arg constructor plus
 * standard getters/setters, so this is a mutable bean rather than a record.
 */
@DynamoDbBean
public class ClickEvent {

    private String shortCode;
    private String clickedAt;

    public ClickEvent() {
    }

    public ClickEvent(String shortCode, String clickedAt) {
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("short_code")
    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("clicked_at")
    public String getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(String clickedAt) {
        this.clickedAt = clickedAt;
    }
}
