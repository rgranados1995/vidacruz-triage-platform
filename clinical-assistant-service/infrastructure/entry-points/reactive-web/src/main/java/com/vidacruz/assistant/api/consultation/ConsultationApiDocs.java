package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.api.consultation.dto.ConsultationRequest;
import com.vidacruz.assistant.api.consultation.dto.ConsultationResponse;
import com.vidacruz.assistant.api.error.ApiError;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

/**
 * OpenAPI documentation of the consultation route, written with the springdoc DSL next to the route.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsultationApiDocs {

    static void consult(Builder operation) {
        operation.operationId("consultarProtocolos").tag("Asistente clínico")
                .summary("Consulta los protocolos clínicos con IA (RF-04)")
                .description("Busca las secciones del corpus oficial más cercanas a la pregunta y redacta la respuesta "
                        + "solo con ellas, citando cada fuente (RN-05). Si ninguna sección es relevante, o si la "
                        + "respuesta del modelo no cita el corpus, responde únicamente el aviso de que no existe un "
                        + "protocolo disponible, sin contenido clínico.")
                .requestBody(requestBodyBuilder().required(true).implementation(ConsultationRequest.class))
                .response(responseBuilder().responseCode("200").description("Respuesta con fuentes, o el aviso")
                        .implementation(ConsultationResponse.class))
                .response(error("400", "Pregunta vacía, muy corta o muy larga (AST-001), o cuerpo inválido (AST-004)"))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("503", "El proveedor de IA no está disponible (AST-002): modo degradado"))
                .response(error("500", "Error inesperado"));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder error(String status, String description) {
        return responseBuilder().responseCode(status).description(description).implementation(ApiError.class);
    }
}
