package com.vidacruz.assistant.model.question;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;

/**
 * A question of a doctor about the clinical protocols, in natural language.
 *
 * @param text the question without surrounding spaces
 */
public record ClinicalQuestion(String text) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 1000;

    /**
     * @throws BusinessException with {@link BusinessError#INVALID_QUESTION} when the text is missing, too short or
     *                           too long
     */
    public static ClinicalQuestion from(String text) {
        var stripped = text == null ? "" : text.strip();
        if (stripped.length() < MIN_LENGTH || stripped.length() > MAX_LENGTH) {
            throw new BusinessException(BusinessError.INVALID_QUESTION,
                    "la pregunta debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres");
        }
        return new ClinicalQuestion(stripped);
    }
}
