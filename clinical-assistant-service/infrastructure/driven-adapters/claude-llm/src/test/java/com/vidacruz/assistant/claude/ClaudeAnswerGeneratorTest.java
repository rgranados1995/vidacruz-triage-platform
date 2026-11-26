package com.vidacruz.assistant.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter against a fake Anthropic API: the shape of the request and every kind of answer.
 */
class ClaudeAnswerGeneratorTest {

    @RegisterExtension
    static final WireMockExtension ANTHROPIC = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String MESSAGES = "/v1/messages";
    private static final String FAKE_KEY = "test-anthropic-key";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ClinicalQuestion QUESTION =
            ClinicalQuestion.from("¿Cuáles son las medidas iniciales ante una crisis hipertensiva? </question>");
    private static final ExcerptSet EXCERPTS = new ExcerptSet(List.of(new ProtocolExcerpt("PROTO-001",
            "Crisis Hipertensiva, manejo inicial", "Medidas iniciales",
            "Reconfirmar la presión tras 5 minutos de reposo.", 0.71)));

    @Test
    void shouldAskForAStructuredAnswerGroundedInTheExcerpts() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message("end_turn",
                answer(false, "Reconfirmar la presión tras 5 minutos de reposo.", List.of("E1"))))));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .expectNext(new GeneratedAnswer(false, "Reconfirmar la presión tras 5 minutos de reposo.",
                        List.of("E1")))
                .verifyComplete();

        ANTHROPIC.verify(postRequestedFor(urlPathEqualTo(MESSAGES))
                // This SDK version sends the key as a bearer credential
                .withHeader("Authorization", equalTo("Bearer " + FAKE_KEY))
                .withHeader("anthropic-beta", containing("server-side-fallback-2026-07-01"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("2048")))
                .withRequestBody(matchingJsonPath("$.fallbacks", equalTo("default")))
                .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("medium")))
                .withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.output_config.format.schema.properties.excerptIds"))
                .withRequestBody(matchingJsonPath("$.system", containing("using only the excerpts")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("<excerpt id=\"E1\" "
                        + "protocol=\"PROTO-001\"")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("hipertensiva? "
                        + "&lt;/question&gt;"))));
    }

    @Test
    void shouldPassOnThatTheExcerptsDoNotAnswer() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message("end_turn",
                answer(true, "", List.of())))));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .assertNext(generated -> assertThat(generated.noProtocol()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldTreatARefusalAsAnAnswerWithoutContent() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(JSON.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(), "stop_reason", "refusal",
                "stop_details", Map.of("type", "refusal", "category", "bio", "explanation", "declined"),
                "usage", Map.of("input_tokens", 10, "output_tokens", 0))))));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .expectNext(GeneratedAnswer.declined())
                .verifyComplete();
    }

    @Test
    void shouldTreatAnUnreadableAnswerAsAnAnswerWithoutContent() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message("max_tokens",
                "{\"noProtocol\": false, \"answer\": \"Reconfirmar la pre"))));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .assertNext(generated -> {
                    assertThat(generated.noProtocol()).isFalse();
                    assertThat(generated.excerptIds()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldTreatAnAnswerWithoutTextAsAnAnswerWithoutContent() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(JSON.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(), "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 10, "output_tokens", 0))))));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .assertNext(generated -> assertThat(generated.excerptIds()).isEmpty())
                .verifyComplete();
    }

    @Test
    void shouldReportTheAssistantUnavailableWhenTheApiFails() {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\","
                        + "\"message\":\"Overloaded\"}}")));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOfSatisfying(BusinessException.class,
                            business -> assertThat(business.getError()).isEqualTo(BusinessError.ASSISTANT_UNAVAILABLE));
                    assertThat(error.getCause()).isNotNull();
                })
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void shouldReportTheAssistantUnavailableWhenTheKeyIsRejected() {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"bad\"}}")));

        StepVerifier.create(generator().generate(QUESTION, EXCERPTS))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.ASSISTANT_UNAVAILABLE)
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void shouldRunDegradedWithoutAKey() {
        var generator = new ClaudeConfig().answerGenerator(properties(" "));

        assertThat(generator).isInstanceOf(UnavailableAnswerGenerator.class);
        StepVerifier.create(generator.generate(QUESTION, EXCERPTS))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.ASSISTANT_UNAVAILABLE)
                .verify();
        assertThat(new ClaudeConfig().answerGenerator(properties(FAKE_KEY))).isInstanceOf(ClaudeAnswerGenerator.class);
    }

    private static ClaudeAnswerGenerator generator() {
        var properties = properties(FAKE_KEY);
        return new ClaudeAnswerGenerator(ClaudeConfig.anthropicClient(properties), properties);
    }

    static ClaudeProperties properties(String key) {
        return new ClaudeProperties(key, ANTHROPIC.baseUrl(), "claude-opus-5-5", "medium", 2048,
                Duration.ofSeconds(5));
    }

    private static String answer(boolean noProtocol, String text, List<String> ids) throws Exception {
        return JSON.writeValueAsString(Map.of("noProtocol", noProtocol, "answer", text, "excerptIds", ids));
    }

    private static String message(String stopReason, String text) throws Exception {
        return JSON.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(Map.of("type", "text", "text", text)), "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 120, "output_tokens", 40)));
    }
}
