package com.vidacruz.triage.api.config;

import com.vidacruz.triage.api.security.ApiKeySecurityConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General data of the OpenAPI document and the API key scheme, so Swagger UI can call the API.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    @Bean
    public OpenAPI triageOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VidaCruz - Servicio de Triage")
                        .version("1.0.0")
                        .description("Registro, clasificación automática (RN-01) y flujo clínico (RF-02) de los "
                                + "pacientes de urgencias. Errores en formato RFC 9457 (application/problem+json)."))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeySecurityConfig.API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
