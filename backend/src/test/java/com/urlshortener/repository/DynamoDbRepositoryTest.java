package com.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.model.UrlMapping;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@ExtendWith(MockitoExtension.class)
class DynamoDbRepositoryTest {

    private static final String TABLE_NAME = "url-mappings";

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<UrlMapping> table;

    private DynamoDbRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The constructor resolves the DynamoDbTable from the enhanced client;
        // return our mocked table so no real AWS call is made.
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(table);
        repository = new DynamoDbRepository(enhancedClient, TABLE_NAME);
    }

    @Test
    @DisplayName("save() delegates to the table's putItem")
    void saveDelegatesToPutItem() {
        UrlMapping mapping = mapping("abc1234");

        repository.save(mapping);

        verify(table).putItem(mapping);
    }

    @Test
    @DisplayName("getByShortCode() returns the mapping when the item exists")
    void getByShortCodeReturnsMappingWhenFound() {
        UrlMapping mapping = mapping("abc1234");
        when(table.getItem(any(Key.class))).thenReturn(mapping);

        Optional<UrlMapping> result = repository.getByShortCode("abc1234");

        assertThat(result).contains(mapping);

        // The lookup must use the short code as the partition key value.
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(table).getItem(keyCaptor.capture());
        assertThat(keyCaptor.getValue().partitionKeyValue().s()).isEqualTo("abc1234");
    }

    @Test
    @DisplayName("getByShortCode() returns empty when the item does not exist")
    void getByShortCodeReturnsEmptyWhenNotFound() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        Optional<UrlMapping> result = repository.getByShortCode("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("exists() returns true when a mapping is present")
    void existsReturnsTrueWhenPresent() {
        when(table.getItem(any(Key.class))).thenReturn(mapping("abc1234"));

        assertThat(repository.exists("abc1234")).isTrue();
    }

    @Test
    @DisplayName("exists() returns false when no mapping is present")
    void existsReturnsFalseWhenAbsent() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        assertThat(repository.exists("missing")).isFalse();
    }

    private static UrlMapping mapping(String shortCode) {
        return new UrlMapping(shortCode, "https://example.com/page", "2026-01-01T00:00:00Z", null);
    }
}
