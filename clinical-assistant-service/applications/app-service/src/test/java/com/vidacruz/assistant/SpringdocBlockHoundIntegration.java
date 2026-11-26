package com.vidacruz.assistant;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * springdoc builds the OpenAPI document once, in a thread of its own at startup ({@code pre-loading-enabled}), under
 * a lock. A request to {@code /v3/api-docs} that arrives while that build is still running waits for the lock on a
 * Netty thread. It only happens in the first moments after startup and only for the documentation, which is disabled
 * in production ({@code API_DOCS_ENABLED}); every other blocking call keeps failing the tests.
 */
public class SpringdocBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("org.springdoc.api.AbstractOpenApiResource", "getOpenApi");
    }
}
