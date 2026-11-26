package com.vidacruz.assistant.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CorpusParserTest {

    @Test
    void shouldReadEverySectionOfAProtocol() {
        var sections = CorpusParser.parse("PROTO-009.md", """
                # PROTO-009 — Protocolo de prueba

                ## Primera sección
                Línea uno.
                Línea dos.

                ## Segunda sección
                Texto.
                """);

        assertThat(sections).containsExactly(
                new CorpusSection("PROTO-009", "Protocolo de prueba", "Primera sección", "Línea uno. Línea dos."),
                new CorpusSection("PROTO-009", "Protocolo de prueba", "Segunda sección", "Texto."));
        assertThat(sections.getFirst().searchableText())
                .isEqualTo("Protocolo de prueba. Primera sección: Línea uno. Línea dos.");
    }

    @Test
    void shouldLoadTheThreeOfficialProtocols() throws Exception {
        var sections = ProtocolCorpusConfig.loadCorpus();

        assertThat(sections).extracting(CorpusSection::protocol).containsOnly("PROTO-001", "PROTO-002", "PROTO-003");
        assertThat(sections).hasSize(7);
        assertThat(sections).anySatisfy(section -> {
            assertThat(section.section()).isEqualTo("Acciones iniciales");
            assertThat(section.content()).contains("Antibiótico de amplio espectro en la primera hora");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Sin encabezado\n## Sección\nTexto.",
        "",
        "# PROTO-009 — Sin secciones\nTexto suelto.",
        "# PROTO-009 — Sección vacía\n## Vacía\n\n## Otra\nTexto.",
        "# PROTO-009 — Sección sin nombre\n## \nTexto."})
    void shouldRejectAMalformedProtocol(String markdown) {
        assertThatIllegalStateException().isThrownBy(() -> CorpusParser.parse("PROTO-009.md", markdown))
                .withMessageStartingWith("PROTO-009.md");
    }

    @Test
    void shouldValidateTheSearchSettings() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolCorpusProperties(0, 0.5, "m", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolCorpusProperties(3, 0, "m", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolCorpusProperties(3, 1, "m", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolCorpusProperties(3, 0.5, " ", "t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProtocolCorpusProperties(3, 0.5, "m", null));
        assertThat(new ProtocolCorpusProperties(1, 0.5, "m", "t").topK()).isEqualTo(1);
    }
}
