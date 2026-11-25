package com.vidacruz.triage.contract.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientEventsTest {

    private static final UUID PATIENT_ID = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final UUID EVENT_ID = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-25T10:04:00Z");

    private final Patient registered = Patient.builder()
            .id(PATIENT_ID).fullName("Ana María Pérez").age(54).chiefComplaint("Dolor torácico")
            .vitalSigns(VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110)
                    .oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                    .build())
            .triageLevel(TriageLevel.CRITICAL).slaMinutes(5).status(PatientStatus.REGISTERED).escalated(false)
            .registeredAt(REGISTERED_AT).statusChangedAt(REGISTERED_AT)
            .build();

    @Test
    void shouldPublishTheRegistrationAsACloudEvent() throws Exception {
        var json = new ObjectMapper().readTree(PatientEvents.toJson(PatientChange.registration(registered), EVENT_ID));

        assertThat(json.get("specversion").asText()).isEqualTo("1.0");
        assertThat(json.get("id").asText()).isEqualTo(EVENT_ID.toString());
        assertThat(json.get("source").asText()).isEqualTo("/vidacruz/triage-service");
        assertThat(json.get("type").asText()).isEqualTo("paciente_registrado");
        assertThat(json.get("subject").asText()).isEqualTo(PATIENT_ID.toString());
        assertThat(json.get("time").asText()).isEqualTo("2026-11-25T10:00:00Z");
        assertThat(json.get("datacontenttype").asText()).isEqualTo("application/json");
        var data = json.get("data");
        assertThat(data.get("pacienteId").asText()).isEqualTo(PATIENT_ID.toString());
        assertThat(data.get("nivel").asText()).isEqualTo("CRITICO");
        assertThat(data.get("estadoAnterior").isNull()).isTrue();
        assertThat(data.get("estado").asText()).isEqualTo("REGISTRADO");
        assertThat(data.get("escalado").asBoolean()).isFalse();
        assertThat(data.get("slaMinutos").asInt()).isEqualTo(5);
        assertThat(data.get("fechaRegistro").asText()).isEqualTo("2026-11-25T10:00:00Z");
        assertThat(data.get("ocurridoEn").asText()).isEqualTo("2026-11-25T10:00:00Z");
    }

    @Test
    void shouldPublishATransitionWithTheStatusBeforeAndAfter() throws Exception {
        var change = PatientChange.transition(registered, PatientStatus.IN_CONSULTATION, LATER);

        var json = new ObjectMapper().readTree(PatientEvents.toJson(change, EVENT_ID));

        assertThat(json.get("type").asText()).isEqualTo("paciente_en_consulta");
        assertThat(json.get("time").asText()).isEqualTo("2026-11-25T10:04:00Z");
        assertThat(json.get("data").get("estadoAnterior").asText()).isEqualTo("REGISTRADO");
        assertThat(json.get("data").get("estado").asText()).isEqualTo("EN_CONSULTA");
    }

    @Test
    void shouldNotPublishPersonalNorClinicalData() throws Exception {
        var text = PatientEvents.toJson(PatientChange.registration(registered), EVENT_ID);
        JsonNode data = new ObjectMapper().readTree(text).get("data");

        assertThat(text).doesNotContain("Ana María Pérez", "Dolor torácico", "130");
        var names = new ArrayList<String>();
        data.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("pacienteId", "nivel", "estadoAnterior", "estado", "escalado",
                "slaMinutos", "fechaRegistro", "ocurridoEn");
    }
}
