package com.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Spring configuration for SQS access via the AWS SDK v2 client, used by the
 * click-event analytics pipeline (Section 17).
 *
 * <p>Follows the same pattern as {@code DynamoDbConfig}: region comes from
 * configuration (environment-backed {@code AWS_REGION}) and credentials are
 * resolved via {@link DefaultCredentialsProvider}, which picks up the Lambda
 * execution role in deployment and standard local credentials in development —
 * never hardcoded.
 */
@Configuration
public class SqsConfig {

    private final String region;

    public SqsConfig(@Value("${app.aws.region}") String region) {
        this.region = region;
    }

    /** SQS client used to publish and consume click events. */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
