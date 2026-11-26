package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.api.config.SecurityHeadersConfig;
import com.vidacruz.assistant.api.error.ApiErrorHandler;
import com.vidacruz.assistant.api.error.ApiErrorWriter;
import com.vidacruz.assistant.api.security.ApiKeySecurityConfig;
import com.vidacruz.assistant.model.answer.AnswerOutcome;
import com.vidacruz.assistant.model.answer.ClinicalAnswer;
import com.vidacruz.assistant.model.answer.GroundedAnswer;
import com.vidacruz.assistant.model.answer.Source;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.usecase.answerclinicalquestion.AnswerClinicalQuestionUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {ConsultationRouter.class, ConsultationHandler.class, ConsultationMetrics.class,
        SimpleMeterRegistry.class, ApiErrorHandler.class, ApiErrorWriter.class, ApiKeySecurityConfig.class,
        SecurityHeadersConfig.class})
@TestPropertySource(properties = {"routes.consultation=/asistente/consulta",
        "errors.unavailable-retry-after=30s", "security.api-key=" + ConsultationRouterTest.KEY})
class ConsultationRouterTest {

    static final String KEY = "test-api-key-0001";
    private static final String CONSULTATION = "/asistente/consulta";
    private static final String HYPERTENSION = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private AnswerClinicalQuestionUseCase answerClinicalQuestion;

    @Test
    void shouldAnswerWithTheSourcesInTheContract() {
        when(answerClinicalQuestion.answer(HYPERTENSION)).thenReturn(Mono.just(new GroundedAnswer(
                new ClinicalAnswer("Reconfirmar la presión tras 5 minutos de reposo.",
                        List.of(new Source("PROTO-001", "Medidas iniciales"))), AnswerOutcome.ANSWERED)));

        consult("{\"pregunta\": \"" + HYPERTENSION + "\"}").expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo("Reconfirmar la presión tras 5 minutos de reposo.")
                .jsonPath("$.fuentes.length()").isEqualTo(1)
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001")
                .jsonPath("$.fuentes[0].seccion").isEqualTo("Medidas iniciales");
    }

    @Test
    void shouldAnswerOnlyTheNoticeWithEmptySources() {
        when(answerClinicalQuestion.answer("¿Tratamiento de la diabetes tipo 2?"))
                .thenReturn(Mono.just(GroundedAnswer.noMatchingProtocol()));

        consult("{\"pregunta\": \"¿Tratamiento de la diabetes tipo 2?\"}").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(ClinicalAnswer.NO_PROTOCOL_NOTICE)
                .jsonPath("$.fuentes").isArray()
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }

    @Test
    void shouldAnswerServiceUnavailableInDegradedMode() {
        when(answerClinicalQuestion.answer(HYPERTENSION)).thenReturn(Mono.error(
                new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, "")));

        consult("{\"pregunta\": \"" + HYPERTENSION + "\"}").expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("AST-002")
                .jsonPath("$.title").isEqualTo("El asistente clínico no está disponible en este momento")
                .jsonPath("$.detail").isEqualTo("Intente de nuevo en unos minutos; mientras tanto, consulte los "
                        + "protocolos clínicos directamente");
    }

    @Test
    void shouldNameTheQuestionWhenItIsInvalid() {
        when(answerClinicalQuestion.answer("a")).thenReturn(Mono.error(new BusinessException(
                BusinessError.INVALID_QUESTION, "la pregunta debe tener entre 3 y 1000 caracteres")));

        consult("{\"pregunta\": \"a\"}").expectStatus().isBadRequest()
                .expectHeader().doesNotExist("Retry-After")
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("AST-001")
                .jsonPath("$.errores[0].campo").isEqualTo("pregunta")
                .jsonPath("$.errores[0].mensaje").isEqualTo("la pregunta debe tener entre 3 y 1000 caracteres");
    }

    @Test
    void shouldRejectABodyThatIsMissingOrNotJson() {
        client.post().uri(CONSULTATION).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("AST-004")
                .jsonPath("$.detail").isEqualTo("El cuerpo de la solicitud es obligatorio");
        consult("{pregunta: ").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("El cuerpo debe ser un JSON válido");
        consult("{\"pregunta\": {\"texto\": 1}}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errores[0].campo").isEqualTo("pregunta");
        verifyNoInteractions(answerClinicalQuestion);
    }

    @Test
    void shouldRequireAValidApiKeyAndHideUnexpectedErrors() {
        client.post().uri(CONSULTATION).contentType(MediaType.APPLICATION_JSON).bodyValue("{\"pregunta\": \"x\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("AST-005");
        when(answerClinicalQuestion.answer(HYPERTENSION))
                .thenReturn(Mono.error(new IllegalStateException("onnx 10.0.0.7 crashed")));

        consult("{\"pregunta\": \"" + HYPERTENSION + "\"}").expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("AST-999").doesNotContain("10.0.0.7"));
    }

    @Test
    void shouldAnswerUnsupportedRoutes() {
        client.get().uri(CONSULTATION).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("AST-006");
    }

    private WebTestClient.ResponseSpec consult(String body) {
        return client.post().uri(CONSULTATION).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }
}
