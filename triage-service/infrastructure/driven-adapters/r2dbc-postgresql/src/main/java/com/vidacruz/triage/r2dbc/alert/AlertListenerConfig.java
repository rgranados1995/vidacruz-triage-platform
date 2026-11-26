package com.vidacruz.triage.r2dbc.alert;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Clock;
import java.time.Duration;

/**
 * The alert listener keeps one connection open for as long as the replica runs, so it gets its own connection built
 * from the same settings instead of holding one of the pool.
 */
@Configuration
public class AlertListenerConfig {

    private static final Duration FIRST_RECONNECT_PAUSE = Duration.ofSeconds(1);
    private static final Duration MAX_RECONNECT_PAUSE = Duration.ofSeconds(30);

    @Bean
    public AlertChangeListener alertChangeListener(R2dbcProperties properties, DatabaseClient client, Clock clock) {
        var options = ConnectionFactoryOptions.parse(properties.getUrl()).mutate()
                .option(ConnectionFactoryOptions.USER, properties.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, properties.getPassword())
                .build();
        return new AlertChangeListener(ConnectionFactories.get(options), client, clock,
                new AlertChangeListener.ReconnectBackoff(FIRST_RECONNECT_PAUSE, MAX_RECONNECT_PAUSE));
    }
}
