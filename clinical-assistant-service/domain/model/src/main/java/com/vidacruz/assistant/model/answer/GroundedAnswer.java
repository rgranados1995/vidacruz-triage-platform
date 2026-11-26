package com.vidacruz.assistant.model.answer;

/**
 * The answer for the doctor together with how it was reached.
 */
public record GroundedAnswer(ClinicalAnswer answer, AnswerOutcome outcome) {

    public static GroundedAnswer noMatchingProtocol() {
        return new GroundedAnswer(ClinicalAnswer.noProtocol(), AnswerOutcome.NO_MATCHING_PROTOCOL);
    }

    static GroundedAnswer withoutContent(AnswerOutcome outcome) {
        return new GroundedAnswer(ClinicalAnswer.noProtocol(), outcome);
    }
}
