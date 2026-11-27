package com.vidacruz.assistant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads one series from the Prometheus endpoint, as the monitoring system scrapes it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PrometheusScrape {

    /** Value of the series {@code name} whose label {@code label} is {@code value}, or -1 when it is not exposed. */
    static double value(WebTestClient client, String name, String label, String value) {
        var body = Objects.requireNonNull(client.get().uri("/prometheus")
                .header("X-API-Key", TestEnvironment.API_KEY).exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody());
        var series = Pattern.compile("(?m)^" + name + "\\{[^}]*" + label + "=\"" + Pattern.quote(value)
                + "\"[^}]*} (\\S+)$").matcher(body);
        return series.find() ? Double.parseDouble(series.group(1)) : -1;
    }
}
