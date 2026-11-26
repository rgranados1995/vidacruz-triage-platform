package com.vidacruz.assistant.model.answer;

/**
 * A section of the official corpus that supports an answer (RN-05).
 *
 * @param protocol protocol code, such as {@code PROTO-001}
 * @param section  section name, such as {@code Medidas iniciales}
 */
public record Source(String protocol, String section) {
}
