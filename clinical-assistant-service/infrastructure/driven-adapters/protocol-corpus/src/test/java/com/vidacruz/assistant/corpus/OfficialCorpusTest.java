package com.vidacruz.assistant.corpus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The corpus is the official one of the statement, word for word: the answers cite it, so a section edited by
 * mistake would make the assistant cite text that the protocol does not say. Every title and section below is copied
 * from the "Corpus de protocolos clínicos" of the statement.
 */
class OfficialCorpusTest {

    private static final List<CorpusSection> OFFICIAL = List.of(
            new CorpusSection("PROTO-001", "Crisis Hipertensiva, manejo inicial", "Definición",
                    "Presión arterial ≥ 180/120 mmHg. Si hay daño de órgano diana es emergencia; si no, urgencia."),
            new CorpusSection("PROTO-001", "Crisis Hipertensiva, manejo inicial", "Medidas iniciales",
                    "Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25% en la "
                            + "primera hora. Vigilar signos de encefalopatía."),
            new CorpusSection("PROTO-001", "Crisis Hipertensiva, manejo inicial", "Contraindicaciones",
                    "Nifedipino sublingual. Reducción intravenosa rápida sin monitorización en cuidados intensivos."),
            new CorpusSection("PROTO-002", "Sepsis, identificación temprana (qSOFA)", "Criterios",
                    "2 o más de: frecuencia respiratoria ≥ 22, alteración del estado mental, presión sistólica "
                            + "≤ 100 mmHg."),
            new CorpusSection("PROTO-002", "Sepsis, identificación temprana (qSOFA)", "Acciones iniciales",
                    "Lactato y hemocultivos antes del antibiótico. Antibiótico de amplio espectro en la primera hora. "
                            + "Fluidoterapia con 30 ml/kg de cristaloides si hay hipotensión o lactato ≥ 4."),
            new CorpusSection("PROTO-003", "Deshidratación pediátrica", "Clasificación",
                    "Leve (pérdida 3-5% del peso), moderada (5-8%), severa (>8% o alteración del estado mental)."),
            new CorpusSection("PROTO-003", "Deshidratación pediátrica", "Rehidratación",
                    "Leve o moderada sin vómito → solución oral 50 ml/kg en 4 horas. Severa → vía intravenosa, bolo "
                            + "de 20 ml/kg."));

    @Test
    void shouldBeExactlyTheCorpusOfTheStatement() throws Exception {
        assertThat(ProtocolCorpusConfig.loadCorpus()).containsExactlyInAnyOrderElementsOf(OFFICIAL);
    }
}
