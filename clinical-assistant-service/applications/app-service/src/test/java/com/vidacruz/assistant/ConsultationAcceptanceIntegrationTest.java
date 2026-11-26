package com.vidacruz.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-04 part A end to end with the three example questions of the statement: real corpus, real embeddings and real
 * validation of the citations; only the Anthropic API is faked, so what the model says is under the control of each
 * test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class ConsultationAcceptanceIntegrationTest {

    @RegisterExtension
    static final WireMockExtension ANTHROPIC = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String MESSAGES = "/v1/messages";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NOTICE = "No existe un protocolo disponible en el corpus oficial para esta pregunta.";

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry, "integration-test-anthropic-key", ANTHROPIC.baseUrl());
    }

    @Test
    void shouldAnswerTheHypertensiveCrisisWithProto001() throws Exception {
        modelAnswers(false, "Reconfirmar la presión tras 5 minutos de reposo, no reducirla más de un 25% en la "
                + "primera hora y vigilar signos de encefalopatía.", List.of("E1"));

        consult("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta")
                .value(answer -> assertThat((String) answer).startsWith("Reconfirmar la presión"))
                .jsonPath("$.fuentes.length()").isEqualTo(1)
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001")
                .jsonPath("$.fuentes[0].seccion").isEqualTo("Medidas iniciales");

        // The model received the matching section of the corpus as excerpt E1
        ANTHROPIC.verify(postRequestedFor(urlPathEqualTo(MESSAGES))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing("id=\"E1\" protocol=\"PROTO-001\""))));
    }

    @Test
    void shouldAnswerTheFirstAntibioticInSepsisWithProto002() throws Exception {
        modelAnswers(false, "El antibiótico de amplio espectro se administra en la primera hora, después de tomar "
                + "lactato y hemocultivos.", List.of("E1"));

        consult("¿Cuándo se administra el primer antibiótico en sepsis?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-002")
                .jsonPath("$.fuentes[0].seccion").isEqualTo("Acciones iniciales");
    }

    @Test
    void shouldAnswerTheRehydrationOfAChildWithProto003() throws Exception {
        modelAnswers(false, "En la deshidratación severa se rehidrata por vía intravenosa con un bolo de 20 ml/kg.",
                List.of("E1"));

        consult("¿Cómo se rehidrata a un niño con deshidratación severa?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fuentes.length()").isEqualTo(1)
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-003");

        // The closest section of the corpus, the one the model was asked to cite as E1, is of PROTO-003
        ANTHROPIC.verify(postRequestedFor(urlPathEqualTo(MESSAGES))
                .withRequestBody(matchingJsonPath("$.messages[0].content",
                        containing("id=\"E1\" protocol=\"PROTO-003\""))));
    }

    @Test
    void shouldAnswerOnlyTheNoticeForDiabetesWithoutCallingTheModel() throws Exception {
        modelAnswers(false, "Metformina como primera línea.", List.of("E1"));

        consult("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(NOTICE)
                .jsonPath("$.fuentes.length()").isEqualTo(0);

        ANTHROPIC.verify(0, postRequestedFor(urlPathEqualTo(MESSAGES)));
    }

    @Test
    void shouldDiscardAnAnswerThatCitesAnExcerptItWasNotGiven() throws Exception {
        modelAnswers(false, "Administrar nifedipino sublingual.", List.of("E9"));

        consult("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(NOTICE)
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }

    @Test
    void shouldAnswerTheNoticeWhenTheModelFindsTheExcerptsDoNotAnswer() throws Exception {
        // "manejo inicial del infarto" passes the similarity threshold: the model is the gate that stops it
        modelAnswers(true, "", List.of());

        consult("¿Cuál es el manejo inicial del infarto agudo de miocardio?").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(NOTICE)
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheProviderFails() {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\","
                        + "\"message\":\"Overloaded\"}}")));

        consult("¿Cuándo se administra el primer antibiótico en sepsis?").expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("AST-002");
    }

    private void modelAnswers(boolean noProtocol, String answer, List<String> ids) throws Exception {
        var text = JSON.writeValueAsString(Map.of("noProtocol", noProtocol, "answer", answer, "excerptIds", ids));
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(JSON.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(Map.of("type", "text", "text", text)), "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 300, "output_tokens", 60))))));
    }

    private WebTestClient.ResponseSpec consult(String question) {
        return client.post().uri("/asistente/consulta").header("X-API-Key", TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", question))
                .exchange();
    }
}
