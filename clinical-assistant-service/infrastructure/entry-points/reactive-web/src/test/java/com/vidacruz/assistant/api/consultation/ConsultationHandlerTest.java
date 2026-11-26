package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.api.consultation.dto.ConsultationRequest;
import com.vidacruz.assistant.api.error.InvalidRequestException;
import com.vidacruz.assistant.model.answer.AnswerOutcome;
import com.vidacruz.assistant.model.answer.ClinicalAnswer;
import com.vidacruz.assistant.model.answer.GroundedAnswer;
import com.vidacruz.assistant.model.answer.Source;
import com.vidacruz.assistant.usecase.answerclinicalquestion.AnswerClinicalQuestionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Handler without a Spring context: what it answers and that it never logs the question.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ConsultationHandlerTest {

    private static final String QUESTION = "Paciente Juan Pérez con presión 190/125, ¿qué hago?";

    @Mock
    private AnswerClinicalQuestionUseCase answerClinicalQuestion;

    @Test
    void shouldAnswerAndLogTheOutcomeButNotTheQuestion(CapturedOutput output) {
        when(answerClinicalQuestion.answer(QUESTION)).thenReturn(Mono.just(new GroundedAnswer(
                new ClinicalAnswer("Reconfirmar la presión.", List.of(new Source("PROTO-001", "Medidas iniciales"))),
                AnswerOutcome.ANSWERED)));

        StepVerifier.create(handler().consult(request(QUESTION)))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(output).contains("Clinical question answered", "outcome=\"ANSWERED\"", "sources=\"1\"")
                .doesNotContain("Juan Pérez", "190/125");
    }

    @Test
    void shouldWarnWhenTheAnswerOfTheModelWasDiscarded(CapturedOutput output) {
        when(answerClinicalQuestion.answer(QUESTION)).thenReturn(Mono.just(new GroundedAnswer(
                ClinicalAnswer.noProtocol(), AnswerOutcome.UNSUPPORTED_ANSWER)));

        StepVerifier.create(handler().consult(request(QUESTION))).expectNextCount(1).verifyComplete();

        assertThat(output).contains("WARN").contains("outcome=\"UNSUPPORTED_ANSWER\"");
    }

    @Test
    void shouldRequireABody() {
        var empty = MockServerRequest.builder().body(Mono.<ConsultationRequest>empty());

        StepVerifier.create(handler().consult(empty)).verifyError(InvalidRequestException.class);
    }

    @Test
    void shouldMapEverySource() {
        var response = ConsultationHandler.toResponse(new GroundedAnswer(new ClinicalAnswer("Texto.", List.of(
                new Source("PROTO-002", "Acciones iniciales"), new Source("PROTO-002", "Criterios"))),
                AnswerOutcome.ANSWERED));

        assertThat(response.answer()).isEqualTo("Texto.");
        assertThat(response.sources()).extracting(source -> source.protocol() + "/" + source.section())
                .containsExactly("PROTO-002/Acciones iniciales", "PROTO-002/Criterios");
    }

    private ConsultationHandler handler() {
        return new ConsultationHandler(answerClinicalQuestion);
    }

    private static MockServerRequest request(String question) {
        return MockServerRequest.builder().body(Mono.just(new ConsultationRequest(question)));
    }
}
