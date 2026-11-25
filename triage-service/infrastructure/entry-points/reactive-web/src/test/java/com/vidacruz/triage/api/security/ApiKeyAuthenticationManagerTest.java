package com.vidacruz.triage.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationManagerTest {

    private final ApiKeyAuthenticationManager manager = new ApiKeyAuthenticationManager("expected-api-key-01");

    @Test
    void shouldAuthenticateTheClientThatSendsTheConfiguredKey() {
        StepVerifier.create(manager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("api-client", "expected-api-key-01")))
                .assertNext(authentication -> {
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getPrincipal()).isEqualTo("api-client");
                    assertThat(authentication.getCredentials()).as("the key is not kept after the check").isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectAnyOtherKey() {
        StepVerifier.create(manager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("api-client", "expected-api-key-02")))
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
