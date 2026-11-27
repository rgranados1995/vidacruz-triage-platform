package com.vidacruz.assistant.model.answer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quantities of a clinical text: numbers alone and with their unit, written in any of the usual ways.
 */
class QuantitiesTest {

    @Test
    void shouldReadEveryNumberAloneAndWithItsUnit() {
        assertThat(Quantities.inText("Bolo de 20 ml/kg; 50 ML/KG en 4 horas, reposo de 5 minutos, 25%, 180/120 mmHg."))
                .containsExactlyInAnyOrder("20", "20 ml/kg", "50", "50 ml/kg", "4", "4 h", "5", "5 min", "25",
                        "25 %", "180", "120", "120 mmhg");
    }

    @Test
    void shouldTreatADecimalCommaAndOtherSpellingsOfAUnitAsTheSame() {
        assertThat(Quantities.inText("0,5 mg y 1 hora y 2 µg y 3 segundos y 7 minuto"))
                .containsExactlyInAnyOrder("0.5", "0.5 mg", "1", "1 h", "2", "2 mcg", "3", "3 s", "7", "7 min");
    }

    @Test
    void shouldIgnoreTheWordAfterANumberWhenItIsNotAUnit() {
        assertThat(Quantities.inText("2 o más de: frecuencia ≥ 22, presión ≤ 100")).containsExactlyInAnyOrder("2", "22",
                "100");
    }

    @Test
    void shouldIgnoreDigitsGluedToAWord() {
        assertThat(Quantities.inText("Según E1 y qSOFA2, sin cifras.")).isEmpty();
        assertThat(Quantities.inText("PROTO-003")).containsExactly("003");
    }
}
