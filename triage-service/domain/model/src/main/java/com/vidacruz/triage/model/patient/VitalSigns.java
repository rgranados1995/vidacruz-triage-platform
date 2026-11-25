package com.vidacruz.triage.model.patient;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Vital signs taken at the reception. Values are nullable because a missing value is a validation error reported to
 * the staff, not a crash. The temperature is a decimal so that thresholds such as 38.5 °C compare exactly.
 *
 * @param heartRate          beats per minute
 * @param systolicPressure   mmHg
 * @param diastolicPressure  mmHg
 * @param oxygenSaturation   SpO2, percentage
 * @param temperature        degrees Celsius
 * @param painLevel          scale from 0 to 10
 * @param glasgowComaScale   GCS, from 3 to 15
 */
@Builder(toBuilder = true)
public record VitalSigns(Integer heartRate,
                         Integer systolicPressure,
                         Integer diastolicPressure,
                         Integer oxygenSaturation,
                         BigDecimal temperature,
                         Integer painLevel,
                         Integer glasgowComaScale) {
}
