package com.vidacruz.triage.api.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Accepts the request when the key it carries is the configured one. The comparison takes the same time whatever
 * the key, so the response time does not reveal how many characters matched.
 */
class ApiKeyAuthenticationManager implements ReactiveAuthenticationManager {

    private final byte[] expectedKey;

    ApiKeyAuthenticationManager(String apiKey) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.just(authentication)
                .filter(candidate -> MessageDigest.isEqual(expectedKey,
                        String.valueOf(candidate.getCredentials()).getBytes(StandardCharsets.UTF_8)))
                .<Authentication>map(candidate ->
                        UsernamePasswordAuthenticationToken.authenticated(candidate.getPrincipal(), null, List.of()))
                .switchIfEmpty(Mono.defer(() -> Mono.error(new BadCredentialsException("Invalid API key"))));
    }
}
