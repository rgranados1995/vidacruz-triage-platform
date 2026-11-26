package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.api.consultation.dto.ConsultationRequest;
import com.vidacruz.assistant.api.consultation.dto.ConsultationResponse;
import com.vidacruz.assistant.api.consultation.dto.SourceResponse;
import com.vidacruz.assistant.api.error.InvalidRequestException;
import com.vidacruz.assistant.model.answer.AnswerOutcome;
import com.vidacruz.assistant.model.answer.GroundedAnswer;
import com.vidacruz.assistant.usecase.answerclinicalquestion.AnswerClinicalQuestionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

/**
 * HTTP side of RF-04 part A. The question is never logged (it may describe a patient): only how the answer was
 * reached and how many sources support it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationHandler {

    private final AnswerClinicalQuestionUseCase answerClinicalQuestion;

    public Mono<ServerResponse> consult(ServerRequest request) {
        return request.bodyToMono(ConsultationRequest.class)
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new InvalidRequestException(null, "El cuerpo de la solicitud es obligatorio"))))
                .flatMap(body -> answerClinicalQuestion.answer(body.question()))
                .doOnNext(ConsultationHandler::logAnswer)
                .flatMap(grounded -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(toResponse(grounded)));
    }

    static ConsultationResponse toResponse(GroundedAnswer grounded) {
        var sources = new ArrayList<SourceResponse>();
        for (var source : grounded.answer().sources()) {
            sources.add(new SourceResponse(source.protocol(), source.section()));
        }
        return new ConsultationResponse(grounded.answer().text(), sources);
    }

    /** A discarded answer of the model is a warning: someone should look at why it did not cite the corpus. */
    private static void logAnswer(GroundedAnswer grounded) {
        var event = grounded.outcome() == AnswerOutcome.UNSUPPORTED_ANSWER ? log.atWarn() : log.atInfo();
        event.addKeyValue("outcome", grounded.outcome())
                .addKeyValue("sources", grounded.answer().sources().size())
                .log("Clinical question answered");
    }
}
