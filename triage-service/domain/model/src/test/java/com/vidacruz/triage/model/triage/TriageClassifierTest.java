package com.vidacruz.triage.model.triage;

import com.vidacruz.triage.model.patient.VitalSigns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriageClassifierTest {

    private static final AdministrativeComplaints ADMINISTRATIVE = new AdministrativeComplaints(List.of(
            "documentos", "recolección de resultados", "renovación de recetario", "vacunación", "seguimiento"));

    /** Vital signs that match no condition of levels 1 to 3. */
    private static final VitalSigns NORMAL = VitalSigns.builder()
            .heartRate(72).systolicPressure(120).diastolicPressure(80).oxygenSaturation(99)
            .temperature(new BigDecimal("36.5")).painLevel(2).glasgowComaScale(15)
            .build();

    private final TriageClassifier classifier = new TriageClassifier(ADMINISTRATIVE);

    /** Patients E1 to E6 of the statement, with the expected level of its table. */
    static List<Arguments> referencePatients() {
        return List.of(
                Arguments.of("E1", VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110)
                        .oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                        .build(), "Dolor torácico", TriageLevel.CRITICAL),
                Arguments.of("E2", VitalSigns.builder().heartRate(95).systolicPressure(150).diastolicPressure(95)
                        .oxygenSaturation(91).temperature(new BigDecimal("37.0")).painLevel(4).glasgowComaScale(15)
                        .build(), "Dificultad para respirar", TriageLevel.URGENT),
                Arguments.of("E3", VitalSigns.builder().heartRate(88).systolicPressure(130).diastolicPressure(85)
                        .oxygenSaturation(98).temperature(new BigDecimal("38.2")).painLevel(6).glasgowComaScale(15)
                        .build(), "Fiebre", TriageLevel.URGENT_2),
                Arguments.of("E4", VitalSigns.builder().heartRate(72).systolicPressure(120).diastolicPressure(80)
                        .oxygenSaturation(99).temperature(new BigDecimal("36.5")).painLevel(2).glasgowComaScale(15)
                        .build(), "Herida por cortar", TriageLevel.NON_URGENT),
                Arguments.of("E5", VitalSigns.builder().heartRate(70).systolicPressure(115).diastolicPressure(75)
                        .oxygenSaturation(99).temperature(new BigDecimal("36.4")).painLevel(0).glasgowComaScale(15)
                        .build(), "Renovación de recetario", TriageLevel.NON_URGENT_2),
                Arguments.of("E6", VitalSigns.builder().heartRate(35).systolicPressure(80).diastolicPressure(50)
                        .oxygenSaturation(85).temperature(new BigDecimal("35.9")).painLevel(0).glasgowComaScale(6)
                        .build(), "Inconsciente", TriageLevel.CRITICAL));
    }

    @ParameterizedTest(name = "{0} -> {3}")
    @MethodSource("referencePatients")
    void shouldClassifyTheReferencePatientsOfTheStatement(String id, VitalSigns signs, String complaint,
                                                          TriageLevel expected) {
        assertThat(classifier.classify(signs, complaint)).as(id).isEqualTo(expected);
    }

    @ParameterizedTest(name = "heart rate {0} -> {1}")
    @CsvSource({"39, CRITICAL", "40, NON_URGENT", "89, NON_URGENT", "90, URGENT_2", "120, URGENT_2", "121, CRITICAL"})
    void shouldApplyTheHeartRateThresholds(int heartRate, TriageLevel expected) {
        var signs = NORMAL.toBuilder().heartRate(heartRate).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "SpO2 {0} -> {1}")
    @CsvSource({"89, CRITICAL", "90, URGENT", "93, URGENT", "94, NON_URGENT"})
    void shouldApplyTheOxygenSaturationThresholds(int oxygenSaturation, TriageLevel expected) {
        var signs = NORMAL.toBuilder().oxygenSaturation(oxygenSaturation).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "systolic {0} -> {1}")
    @CsvSource({"89, CRITICAL", "90, URGENT", "100, URGENT", "101, NON_URGENT"})
    void shouldApplyTheSystolicPressureThresholds(int systolicPressure, TriageLevel expected) {
        var signs = NORMAL.toBuilder().systolicPressure(systolicPressure).diastolicPressure(60).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "temperature {0} -> {1}")
    @CsvSource({"37.9, NON_URGENT", "38.0, URGENT_2", "38.4, URGENT_2", "38.49, URGENT_2", "38.5, URGENT",
            "38.50, URGENT", "41.0, URGENT"})
    void shouldApplyTheTemperatureThresholds(String temperature, TriageLevel expected) {
        var signs = NORMAL.toBuilder().temperature(new BigDecimal(temperature)).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "GCS {0} -> {1}")
    @CsvSource({"3, CRITICAL", "8, CRITICAL", "9, URGENT", "13, URGENT", "14, NON_URGENT"})
    void shouldApplyTheGlasgowComaScaleThresholds(int glasgowComaScale, TriageLevel expected) {
        var signs = NORMAL.toBuilder().glasgowComaScale(glasgowComaScale).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "pain {0} -> {1}")
    @CsvSource({"6, NON_URGENT", "7, URGENT_2", "10, URGENT_2"})
    void shouldApplyThePainThreshold(int painLevel, TriageLevel expected) {
        var signs = NORMAL.toBuilder().painLevel(painLevel).build();

        assertThat(classifier.classify(signs, "")).isEqualTo(expected);
    }

    @Test
    void shouldKeepTheMostSevereLevelWhenConditionsOfSeveralLevelsMatch() {
        var criticalAndUrgent = NORMAL.toBuilder().heartRate(130).oxygenSaturation(91).build();
        var urgentAndUrgent2 = NORMAL.toBuilder().oxygenSaturation(91).painLevel(9).build();

        assertThat(classifier.classify(criticalAndUrgent, "")).isEqualTo(TriageLevel.CRITICAL);
        assertThat(classifier.classify(urgentAndUrgent2, "")).isEqualTo(TriageLevel.URGENT);
    }

    @Test
    void shouldLowerToLevelFiveOnlyNonUrgentPatientsWithAnAdministrativeComplaint() {
        var urgent = NORMAL.toBuilder().oxygenSaturation(91).build();

        assertThat(classifier.classify(NORMAL, "  RENOVACION de   recetario ")).isEqualTo(TriageLevel.NON_URGENT_2);
        assertThat(classifier.classify(urgent, "Documentos")).isEqualTo(TriageLevel.URGENT);
        assertThat(classifier.classify(NORMAL, "Fiebre tras vacunación")).isEqualTo(TriageLevel.NON_URGENT);
        assertThat(classifier.classify(NORMAL, null)).isEqualTo(TriageLevel.NON_URGENT);
    }
}
