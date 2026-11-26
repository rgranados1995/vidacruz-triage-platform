package com.vidacruz.assistant.api.config;

import com.vidacruz.assistant.api.security.ApiKeySecurityConfig;
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
    public OpenAPI assistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VidaCruz - Asistente Clínico")
                        .version("1.0.0")
                        .description("Consultas en lenguaje natural sobre los protocolos clínicos oficiales (RF-04), "
                                + "siempre con cita de la fuente y sin contenido fuera del corpus (RN-05). Errores en "
                                + "formato RFC 9457 (application/problem+json)."))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeySecurityConfig.API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
