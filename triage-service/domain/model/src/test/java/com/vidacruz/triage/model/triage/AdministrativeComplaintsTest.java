package com.vidacruz.triage.model.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdministrativeComplaintsTest {

    private final AdministrativeComplaints complaints =
            new AdministrativeComplaints(List.of("Recolección de resultados", "vacunación"));

    @ParameterizedTest(name = "\"{0}\" is administrative")
    @ValueSource(strings = {"recolección de resultados", "RECOLECCION DE RESULTADOS", "  recoleccion   de resultados ",
            "Vacunacion", "vacunación"})
    void shouldMatchTheSamePhraseIgnoringCaseAccentsAndSpaces(String complaint) {
        assertThat(complaints.contains(complaint)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is not administrative")
    @ValueSource(strings = {"fiebre tras vacunación", "vacunaciones", "resultados", ""})
    void shouldNotMatchAComplaintThatOnlyMentionsAnAdministrativeWord(String complaint) {
        assertThat(complaints.contains(complaint)).isFalse();
    }

    @Test
    void shouldNotMatchAMissingComplaint() {
        assertThat(complaints.contains(null)).isFalse();
    }
}
