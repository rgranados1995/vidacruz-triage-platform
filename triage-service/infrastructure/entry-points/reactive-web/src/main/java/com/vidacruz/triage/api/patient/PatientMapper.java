package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.patient.dto.PatientDetailResponse;
import com.vidacruz.triage.api.patient.dto.RegisterPatientRequest;
import com.vidacruz.triage.api.patient.dto.RegisteredPatientResponse;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.usecase.getpatient.PatientDetail;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Translates between the HTTP contract and the domain. Values pass through unchanged (nulls included): validating
 * them is the job of the domain.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientMapper {

    static PatientIntake toIntake(RegisterPatientRequest request) {
        return PatientIntake.builder()
                .fullName(request.fullName())
                .age(request.age())
                .chiefComplaint(request.chiefComplaint())
                .vitalSigns(VitalSigns.builder()
                        .heartRate(request.heartRate())
                        .systolicPressure(request.systolicPressure())
                        .diastolicPressure(request.diastolicPressure())
                        .oxygenSaturation(request.oxygenSaturation())
                        .temperature(request.temperature())
                        .painLevel(request.painLevel())
                        .glasgowComaScale(request.glasgowComaScale())
                        .build())
                .build();
    }

    static RegisteredPatientResponse toRegistered(Patient patient) {
        return new RegisteredPatientResponse(patient.getId(), ContractCodes.levelCode(patient.getTriageLevel()),
                patient.getSlaMinutes(), ContractCodes.statusCode(patient.getStatus()), patient.getRegisteredAt());
    }

    static PatientDetailResponse toDetail(PatientDetail detail) {
        var patient = detail.patient();
        var signs = patient.getVitalSigns();
        return PatientDetailResponse.builder()
                .id(patient.getId())
                .fullName(patient.getFullName())
                .age(patient.getAge())
                .heartRate(signs.heartRate())
                .systolicPressure(signs.systolicPressure())
                .diastolicPressure(signs.diastolicPressure())
                .oxygenSaturation(signs.oxygenSaturation())
                .temperature(signs.temperature())
                .painLevel(signs.painLevel())
                .glasgowComaScale(signs.glasgowComaScale())
                .chiefComplaint(patient.getChiefComplaint())
                .level(ContractCodes.levelCode(patient.getTriageLevel()))
                .slaMinutes(patient.getSlaMinutes())
                .status(ContractCodes.statusCode(patient.getStatus()))
                .escalated(patient.isEscalated())
                .registeredAt(patient.getRegisteredAt())
                .statusChangedAt(patient.getStatusChangedAt())
                .consultationStartedAt(patient.getConsultationStartedAt())
                .timeInCurrentStatus(detail.timeInCurrentStatus().toString())
                .build();
    }
}
