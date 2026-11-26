package com.vidacruz.assistant.model.question;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalQuestionTest {

    @Test
    void shouldKeepTheQuestionWithoutSurroundingSpaces() {
        assertThat(ClinicalQuestion.from("  ¿Cuándo se administra el antibiótico?  ").text())
                .isEqualTo("¿Cuándo se administra el antibiótico?");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 1000})
    void shouldAcceptAQuestionAtTheLimits(int length) {
        assertThat(ClinicalQuestion.from("a".repeat(length)).text()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 1001})
    void shouldRejectAQuestionOutsideTheLimits(int length) {
        var text = "a".repeat(length);

        assertThatThrownBy(() -> ClinicalQuestion.from(text))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getError()).isEqualTo(BusinessError.INVALID_QUESTION);
                    assertThat(error.getDetail()).isEqualTo("la pregunta debe tener entre 3 y 1000 caracteres");
                });
    }

    @Test
    void shouldRejectAMissingOrBlankQuestion() {
        assertThatThrownBy(() -> ClinicalQuestion.from(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ClinicalQuestion.from("     ")).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldKeepTheCauseOfAnUnavailableAssistant() {
        var cause = new IllegalStateException("timeout");
        var error = new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, "", cause);

        assertThat(error).hasCause(cause).hasMessage("AST-002 El asistente clínico no está disponible en este momento");
    }
}
