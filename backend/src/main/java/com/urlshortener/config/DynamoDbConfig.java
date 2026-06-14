package com.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Spring configuration for DynamoDB access via the AWS SDK v2 enhanced client.
 *
 * <p>The clients are defined as singleton beans so they are created once and
 * shared (and easily mocked in tests). Region comes from configuration
 * (environment-backed {@code AWS_REGION}); credentials are resolved via
 * {@link DefaultCredentialsProvider}, which picks up the Lambda execution role
 * in deployment and standard local credentials in development — never hardcoded.
 */
@Configuration
public class DynamoDbConfig {

    private final String region;

    public DynamoDbConfig(@Value("${app.aws.region}") String region) {
        this.region = region;
    }

    /** Low-level DynamoDB client, backing the enhanced client below. */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /** Bean-mapped enhanced client used by repositories for typed access. */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
