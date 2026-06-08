package com.urlshortener.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * DynamoDB entity for the {@code url-mappings} table.
 *
 * <p>Partition key is {@code short_code}; every access pattern in the MVP is a
 * single key lookup. Timestamps are stored as ISO-8601 strings per the table
 * design in the project spec. {@code expires_at} is nullable — {@code null}
 * means the link never expires (TTL logic itself is a V2 feature).
 *
 * <p>The DynamoDB enhanced client requires a public no-arg constructor plus
 * standard getters/setters, so this is a mutable bean rather than a record.
 */
@DynamoDbBean
public class UrlMapping {

    private String shortCode;
    private String longUrl;
    private String createdAt;
    private String expiresAt;

    public UrlMapping() {
    }

    public UrlMapping(String shortCode, String longUrl, String createdAt, String expiresAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("short_code")
    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    @DynamoDbAttribute("long_url")
    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("expires_at")
    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
