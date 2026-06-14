package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Persistent storage for {@link UrlMapping} items in the {@code url-mappings}
 * DynamoDB table, using the AWS SDK v2 enhanced (bean-mapped) client.
 *
 * <p>The {@link DynamoDbEnhancedClient} is injected (configured in
 * {@code DynamoDbConfig}); the table name comes from configuration
 * (environment-backed {@code DYNAMODB_TABLE_NAME}), never hardcoded.
 *
 * <p>Scope is MVP only: a single-key access pattern by {@code short_code}.
 * No GSIs, no scans, no delete/update.
 */
@Repository
public class DynamoDbRepository {

    private final DynamoDbTable<UrlMapping> table;

    public DynamoDbRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(UrlMapping.class));
    }

    /**
     * Persists a mapping. Overwrites any existing item with the same
     * {@code short_code}; callers ensure uniqueness before saving.
     *
     * @param mapping the mapping to store
     */
    public void save(UrlMapping mapping) {
        table.putItem(mapping);
    }

    /**
     * Looks up a mapping by its short code (single partition-key read).
     *
     * @param shortCode the short code to resolve
     * @return the mapping, or empty if no item exists for that code
     */
    public Optional<UrlMapping> getByShortCode(String shortCode) {
        Key key = Key.builder().partitionValue(shortCode).build();
        return Optional.ofNullable(table.getItem(key));
    }

    /**
     * Reports whether a short code is already taken. Suitable as the collision
     * check for {@code ShortCodeGenerator#generateUnique}.
     *
     * @param shortCode the short code to check
     * @return {@code true} if an item already exists for that code
     */
    public boolean exists(String shortCode) {
        return getByShortCode(shortCode).isPresent();
    }
}
