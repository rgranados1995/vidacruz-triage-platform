package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.alert.dto.AcknowledgeAlertRequest;
import com.vidacruz.triage.api.alert.dto.AlertResponse;
import com.vidacruz.triage.api.error.ApiError;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.arrayschema.Builder.arraySchemaBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

/**
 * OpenAPI documentation of the alert routes, written with the springdoc DSL next to the routes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlertApiDocs {

    private static final String TAG = "Alertas";
    private static final String UNAUTHORIZED = "Falta la API key o no es válida";

    static void listActive(Builder operation) {
        operation.operationId("listarAlertasActivas").tag(TAG)
                .summary("Alertas de escalamiento activas (RF-06)")
                .description("Pendientes y reconocidas, de la más grave a la menos grave y luego por antigüedad.")
                .response(responseBuilder().responseCode("200").description("Alertas activas")
                        .content(contentBuilder().mediaType("application/json")
                                .array(arraySchemaBuilder()
                                        .schema(schemaBuilder().implementation(AlertResponse.class)))))
                .response(error("401", UNAUTHORIZED))
                .response(error("500", "Error inesperado"));
    }

    static void stream(Builder operation) {
        operation.operationId("seguirAlertas").tag(TAG)
                .summary("Alertas en tiempo real (Server-Sent Events)")
                .description("Al conectarse envía las alertas activas y luego cada cambio (evento \"alerta\" con "
                        + "la alerta completa: creada, reconocida o resuelta). Una alerta puede llegar dos veces: "
                        + "vale la de actualizadaEn más reciente. Envía un comentario \"latido\" mientras no hay "
                        + "cambios. Si se corta, basta con reconectarse: se reciben de nuevo las activas.")
                .response(responseBuilder().responseCode("200").description("Stream de alertas")
                        .content(contentBuilder().mediaType("text/event-stream")
                                .schema(schemaBuilder().implementation(AlertResponse.class))))
                .response(error("401", UNAUTHORIZED));
    }

    static void acknowledge(Builder operation) {
        operation.operationId("reconocerAlerta").tag(TAG)
                .summary("Reconoce una alerta pendiente")
                .description("Deja registrado quién la tomó, para que el resto del equipo lo sepa. El responsable "
                        + "lo informa el cliente: la API key es del sistema, no de la persona.")
                .parameter(parameterBuilder().in(ParameterIn.PATH).name(AlertHandler.ID_VARIABLE).required(true)
                        .description("Id de la alerta").implementation(Long.class))
                .requestBody(requestBodyBuilder().required(true).implementation(AcknowledgeAlertRequest.class))
                .response(responseBuilder().responseCode("200").description("Alerta reconocida")
                        .implementation(AlertResponse.class))
                .response(error("400", "Id que no es un número o responsable inválido"))
                .response(error("401", UNAUTHORIZED))
                .response(error("404", "La alerta no existe"))
                .response(error("409", "La alerta ya fue reconocida o resuelta"))
                .response(error("500", "Error inesperado"));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder error(String status, String description) {
        return responseBuilder().responseCode(status).description(description).implementation(ApiError.class);
    }
}
