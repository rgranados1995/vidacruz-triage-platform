package com.vidacruz.assistant.claude;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.vidacruz.assistant.model.answer.Claim;
import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Drafts the answer with Claude through the official Anthropic SDK, asynchronously (no thread waits for the API).
 * The answer comes back as structured JSON ({@link ModelAnswer}); server-side fallbacks are on, so a request refused
 * by one model is retried by the API on another. Whatever the model returns is checked afterwards against the
 * excerpts (RN-05); here a refusal or an unreadable answer only becomes an answer without content. Every call goes
 * through the resilience policy ({@link ClaudeResilience}); whatever it gives up on becomes the explicit fallback of
 * the degraded mode: {@link BusinessError#ASSISTANT_UNAVAILABLE}, answered as 503.
 */
@Slf4j
public class ClaudeAnswerGenerator implements AnswerGenerator {

    private static final GeneratedAnswer UNREADABLE = new GeneratedAnswer(false, List.of());

    private final AnthropicClientAsync client;
    private final ClaudeProperties properties;
    private final ClaudeResilience resilience;

    public ClaudeAnswerGenerator(AnthropicClientAsync client, ClaudeProperties properties,
                                 ClaudeResilience resilience) {
        this.client = client;
        this.properties = properties;
        this.resilience = resilience;
    }

    @Override
    public Mono<GeneratedAnswer> generate(ClinicalQuestion question, ExcerptSet excerpts) {
        return Mono.fromCallable(() -> request(question, excerpts))
                // suppressCancel = false: when the time limit cancels an attempt, its HTTP call is cancelled too,
                // so it neither keeps a connection busy nor fails later with nobody listening
                .flatMap(request -> resilience.protect(
                        Mono.fromFuture(() -> client.beta().messages().create(request), false)))
                .doOnNext(this::logAnswer)
                .map(ClaudeAnswerGenerator::toGenerated)
                .onErrorResume(AnthropicInvalidDataException.class, error -> {
                    log.atWarn().setCause(error).log("Claude answer could not be read");
                    return Mono.just(UNREADABLE);
                })
                // Any other failure (HTTP status, timeout, connection) means the model cannot be reached now
                .onErrorMap(error -> new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, "", error));
    }

    private StructuredMessageCreateParams<ModelAnswer> request(ClinicalQuestion question, ExcerptSet excerpts) {
        return MessageCreateParams.builder()
                .outputConfig(ModelAnswer.class, BetaOutputConfig.Effort.of(properties.effort()))
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(ClaudePrompt.SYSTEM)
                .addUserMessage(ClaudePrompt.userMessage(question, excerpts))
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .build();
    }

    /** A refusal, or an answer without text, carries no clinical content. */
    static GeneratedAnswer toGenerated(StructuredMessage<ModelAnswer> message) {
        if (message.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            return GeneratedAnswer.declined();
        }
        for (var block : message.content()) {
            var text = block.text();
            if (text.isPresent()) {
                var answer = text.get().text();
                return new GeneratedAnswer(answer.noProtocol(), claims(answer.claims()));
            }
        }
        return UNREADABLE;
    }

    /** A missing list or sentence stays missing: the excerpt set rejects it. */
    private static List<Claim> claims(List<ModelAnswer.ModelClaim> claims) {
        var converted = new ArrayList<Claim>();
        for (var claim : claims == null ? List.<ModelAnswer.ModelClaim>of() : claims) {
            converted.add(claim == null ? null : new Claim(claim.text(), claim.excerptIds()));
        }
        return converted;
    }

    private void logAnswer(StructuredMessage<ModelAnswer> message) {
        log.atInfo()
                .addKeyValue("model", properties.model())
                .addKeyValue("stopReason", message.stopReason().map(Object::toString).orElse(""))
                .addKeyValue("refusalCategory", message.stopDetails()
                        .flatMap(BetaRefusalStopDetails::category).map(Object::toString).orElse(""))
                .log("Claude answered");
    }
}
