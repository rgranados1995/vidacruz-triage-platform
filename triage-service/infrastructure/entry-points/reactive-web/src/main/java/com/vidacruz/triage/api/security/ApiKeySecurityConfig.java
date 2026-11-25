package com.vidacruz.triage.api.security;

import com.vidacruz.triage.api.error.ApiErrorWriter;
import com.vidacruz.triage.api.error.ApiErrors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * API key authentication (B1 of D4): every route of the API needs the {@code X-API-Key} header; the probes, the
 * metrics and the API documentation stay public because they expose no patient data. Stateless: no session, no
 * CSRF, no login form.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeySecurityConfig {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final String CLIENT = "api-client";
    private static final String[] PUBLIC_PATHS = {"/health", "/health/**", "/prometheus", "/v3/api-docs",
        "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"};

    @Bean
    public SecurityWebFilterChain apiSecurityFilterChain(ServerHttpSecurity http, ApiKeyProperties properties,
                                                         ApiErrorWriter writer) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, error) ->
                writer.write(exchange, ApiErrors.unauthorized(exchange.getRequest().getPath().value()));
        var apiKeyFilter = new AuthenticationWebFilter(new ApiKeyAuthenticationManager(properties.apiKey()));
        apiKeyFilter.setServerAuthenticationConverter(ApiKeySecurityConfig::readApiKey);
        apiKeyFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(unauthorized));
        apiKeyFilter.setRequiresAuthenticationMatcher(
                new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.pathMatchers(PUBLIC_PATHS)));
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized))
                .build();
    }

    private static Mono<Authentication> readApiKey(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER))
                .map(key -> UsernamePasswordAuthenticationToken.unauthenticated(CLIENT, key));
    }
}
