package com.vidacruz.assistant.claude;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Classifies the failures of the calls to the AI provider for the resilience policy.
 * <ul>
 *     <li>Transient: worth another attempt (rate limit, server error or overload, timeout, network).</li>
 *     <li>Outage: the provider cannot serve us now, so the circuit breaker counts it. Transient failures plus a
 *     rejected key (401, 403), which no retry fixes. A malformed request of ours (400, 404, 422) is neither: it must
 *     not take the assistant down for every doctor.</li>
 * </ul>
 * Public because Resilience4j instantiates the nested predicates by reflection from the configuration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderFailures {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REQUEST_TIMEOUT = 408;
    private static final int FIRST_SERVER_ERROR = 500;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    static boolean isTransient(Throwable error) {
        var failure = unwrap(error);
        return switch (failure) {
            case TimeoutException ignored -> true;
            case AnthropicIoException ignored -> true;
            case AnthropicServiceException service -> service.statusCode() == TOO_MANY_REQUESTS
                    || service.statusCode() == REQUEST_TIMEOUT || service.statusCode() >= FIRST_SERVER_ERROR;
            default -> false;
        };
    }

    static boolean isOutage(Throwable error) {
        return isTransient(error) || unwrap(error) instanceof AnthropicServiceException service
                && (service.statusCode() == UNAUTHORIZED || service.statusCode() == FORBIDDEN);
    }

    /** The asynchronous client may wrap the failure of the call. */
    static Throwable unwrap(Throwable error) {
        var failure = error;
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /** Retry predicate of the {@code claude} instance ({@code retry-exception-predicate}). */
    public static final class Transient implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable error) {
            return isTransient(error);
        }
    }

    /** Circuit breaker predicate of the {@code claude} instance ({@code record-exception-predicate}). */
    public static final class Outage implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable error) {
            return isOutage(error);
        }
    }
}
