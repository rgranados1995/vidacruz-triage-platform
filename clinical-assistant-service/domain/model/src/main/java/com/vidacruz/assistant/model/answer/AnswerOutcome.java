package com.vidacruz.assistant.model.answer;

/**
 * Why an answer came out the way it did; logged (and measured later) without exposing it to the doctor.
 */
public enum AnswerOutcome {

    /** Clinical content supported by sources of the corpus. */
    ANSWERED,
    /** No section of the corpus was close enough: the model was not called. */
    NO_MATCHING_PROTOCOL,
    /** The model said the excerpts do not answer the question, or refused. */
    DECLINED_BY_MODEL,
    /** The model answered without a valid citation: the answer was discarded (RN-05). */
    UNSUPPORTED_ANSWER
}
