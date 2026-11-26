package com.vidacruz.assistant.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersConfigTest {

    @Test
    void shouldAddEverySecurityHeaderBeforeContinuingTheChain() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/asistente/consulta").build());
        var chainCalled = new boolean[1];

        StepVerifier.create(new SecurityHeadersConfig().filter(exchange, ignored -> {
                    chainCalled[0] = true;
                    return Mono.empty();
                }))
                .verifyComplete();

        var headers = exchange.getResponse().getHeaders();
        assertThat(chainCalled[0]).isTrue();
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; frame-ancestors 'self'; form-action 'self'");
        assertThat(headers.getFirst("Strict-Transport-Security")).isEqualTo("max-age=31536000;");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Server")).isEmpty();
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }
}
