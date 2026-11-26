package com.vidacruz.assistant.api.urgency;

import com.vidacruz.assistant.api.error.ApiError;
import com.vidacruz.assistant.api.urgency.dto.UrgencyStateResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

/**
 * OpenAPI documentation of the urgency state route, written with the springdoc DSL next to the route.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UrgencyStateApiDocs {

    static void current(Builder operation) {
        operation.operationId("estadoUrgencias").tag("Estado de urgencias")
                .summary("Pacientes activos por nivel de triaje (RF-04)")
                .description("Pacientes que no están en ALTA, por nivel. El asistente lo reconstruye consumiendo "
                        + "triaje.eventos y lo guarda en Redis, sin llamar a la API del triaje; por eso es "
                        + "eventualmente consistente (ver actualizadoEn).")
                .response(responseBuilder().responseCode("200").description("Estado actual")
                        .implementation(UrgencyStateResponse.class))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("503", "El almacenamiento del estado no está disponible (AST-003)"))
                .response(error("500", "Error inesperado"));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder error(String status, String description) {
        return responseBuilder().responseCode(status).description(description).implementation(ApiError.class);
    }
}
