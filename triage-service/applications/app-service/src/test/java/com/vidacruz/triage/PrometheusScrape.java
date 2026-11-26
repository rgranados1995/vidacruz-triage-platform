package com.vidacruz.triage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads a counter of the triage from the Prometheus endpoint, as the monitoring system scrapes it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PrometheusScrape {

    /** Value of the series of the level, or -1 when the series is not exposed. */
    static double counter(WebTestClient client, String name, String level) {
        var body = Objects.requireNonNull(client.get().uri("/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody());
        var series = Pattern.compile("(?m)^" + name + "\\{application=\"triage-service\",level=\"" + level
                + "\"} (\\S+)$").matcher(body);
        return series.find() ? Double.parseDouble(series.group(1)) : -1;
    }
}
