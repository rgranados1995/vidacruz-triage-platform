package com.vidacruz.assistant.api.consultation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Answer of RF-04 part A: clinical content with its sources, or only the notice that no protocol covers the question
 * (with no sources and no clinical content).
 */
@Schema(name = "RespuestaConsulta")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConsultationResponse(
        @JsonProperty("respuesta")
        @Schema(description = "Respuesta basada solo en el corpus, o el aviso \"No existe un protocolo disponible en "
                + "el corpus oficial para esta pregunta.\"",
                example = "Reconfirmar la presión tras 5 minutos de reposo, no reducirla más de un 25% en la primera "
                        + "hora y vigilar signos de encefalopatía.")
        String answer,
        @JsonProperty("fuentes")
        @ArraySchema(arraySchema = @Schema(description = "Secciones del corpus que sustentan la respuesta; vacío en "
                + "el aviso"))
        List<SourceResponse> sources) {
}
