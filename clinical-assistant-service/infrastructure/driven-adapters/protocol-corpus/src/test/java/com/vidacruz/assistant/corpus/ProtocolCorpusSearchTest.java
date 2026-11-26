package com.vidacruz.assistant.corpus;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real search: official corpus, real multilingual embedding model and the threshold of the deployment (0.50).
 * Calibrated on 2026-11-26: the relevant questions scored 0.586 to 0.758, the unrelated ones 0.037 to 0.440, and
 * "manejo inicial del infarto" 0.531 (it passes the threshold; the language model is the gate that stops it).
 */
class ProtocolCorpusSearchTest {

    static final double THRESHOLD = 0.50;

    private static VectorStoreProtocolRetriever retriever;

    @BeforeAll
    static void loadTheCorpus() throws Exception {
        var properties = properties(3, THRESHOLD);
        var config = new ProtocolCorpusConfig();
        retriever = config.protocolRetriever(config.protocolVectorStore(config.protocolEmbeddingModel(properties)),
                properties);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "¿Cuáles son las medidas iniciales ante una crisis hipertensiva? | PROTO-001 | Medidas iniciales",
        "¿Cuándo se administra el primer antibiótico en sepsis?          | PROTO-002 | Acciones iniciales",
        "Tengo un paciente con presión 190/125, ¿qué hago primero?       | PROTO-001 | Medidas iniciales",
        "¿Se puede bajar rápido la presión en una emergencia hipertensiva? | PROTO-001 | Medidas iniciales",
        "¿Cómo se rehidrata a un niño con deshidratación severa?         | PROTO-003 | Rehidratación",
        "¿Cuánto suero oral le doy a un niño con deshidratación leve?    | PROTO-003 | Rehidratación"})
    void shouldFindTheSectionThatAnswersTheQuestionFirst(String question, String protocol, String section) {
        StepVerifier.create(retriever.retrieve(ClinicalQuestion.from(question)).next())
                .assertNext(best -> {
                    assertThat(best.protocol()).isEqualTo(protocol);
                    assertThat(best.section()).isEqualTo(section);
                    assertThat(best.similarity()).isGreaterThanOrEqualTo(THRESHOLD);
                    assertThat(best.content()).isNotBlank().doesNotStartWith(best.title());
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?",
        "¿Qué dosis de insulina se usa en cetoacidosis diabética?",
        "¿Cómo se trata una fractura de cadera?",
        "¿Qué vacunas necesita un recién nacido?",
        "Dosis de paracetamol en adultos",
        "¿Cuál es el tratamiento de la neumonía adquirida en la comunidad?",
        "¿Cuál es la capital de Francia?"})
    void shouldFindNothingForAQuestionNoProtocolCovers(String question) {
        StepVerifier.create(retriever.retrieve(ClinicalQuestion.from(question))).verifyComplete();
    }

    @Test
    void shouldHandAtMostTopKSectionsAboveTheThresholdTheClosestFirst() {
        StepVerifier.create(retriever.retrieve(ClinicalQuestion.from(
                        "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")).collectList())
                .assertNext(found -> {
                    assertThat(found).hasSizeBetween(1, 3).allMatch(excerpt -> excerpt.similarity() >= THRESHOLD);
                    assertThat(found).extracting(ProtocolExcerpt::similarity)
                            .isSortedAccordingTo((a, b) -> Double.compare(b, a));
                    assertThat(found.getFirst().title()).isEqualTo("Crisis Hipertensiva, manejo inicial");
                })
                .verifyComplete();
    }

    static ProtocolCorpusProperties properties(int topK, double threshold) {
        var dir = new File(System.getProperty("embedding.model.dir"));
        return new ProtocolCorpusProperties(topK, threshold, new File(dir, "model.onnx").toURI().toString(),
                new File(dir, "tokenizer.json").toURI().toString());
    }
}
