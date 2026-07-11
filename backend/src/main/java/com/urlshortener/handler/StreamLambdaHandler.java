package com.urlshortener.handler;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.urlshortener.UrlShortenerApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * AWS Lambda entry point for the HTTP-triggered Spring Boot app, wrapping
 * {@link SpringBootLambdaContainerHandler} so API Gateway proxy events are
 * dispatched into the normal Spring MVC controllers.
 *
 * <p>This single handler class is shared by <b>both</b> the shorten and redirect
 * Lambda functions — they run the same code and differ only in function identity
 * and network configuration (redirect is VPC-attached, shorten is not). API
 * Gateway routes {@code POST /shorten} and {@code GET /{code}} to the two
 * separate function ARNs that both point at this class.
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(UrlShortenerApplication.class);
        } catch (ContainerInitializationException e) {
            // A failure here means the app cannot start — surface it immediately.
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
            throws IOException {
        handler.proxyStream(inputStream, outputStream, context);
    }
}
