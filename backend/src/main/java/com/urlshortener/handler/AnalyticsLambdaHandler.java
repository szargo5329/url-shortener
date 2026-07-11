package com.urlshortener.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.urlshortener.UrlShortenerApplication;
import com.urlshortener.service.AnalyticsEventConsumer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * AWS Lambda entry point for the SQS-triggered analytics function.
 *
 * <p>{@link AnalyticsEventConsumer} only implements the generic
 * {@code Consumer<String>} shape, so this class is the AWS-specific adapter: it
 * receives the {@link SQSEvent} batch delivered by the event source mapping and
 * hands each message body to the consumer.
 *
 * <p>A non-web Spring context is booted once (statically) so the consumer is the
 * fully-wired bean, with its DynamoDB client and configuration injected.
 */
public class AnalyticsLambdaHandler implements RequestHandler<SQSEvent, Void> {

    private static final ConfigurableApplicationContext CONTEXT =
            new SpringApplicationBuilder(UrlShortenerApplication.class)
                    .web(WebApplicationType.NONE)
                    .run();

    private final AnalyticsEventConsumer consumer = CONTEXT.getBean(AnalyticsEventConsumer.class);

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSMessage message : event.getRecords()) {
            consumer.accept(message.getBody());
        }
        return null;
    }
}
