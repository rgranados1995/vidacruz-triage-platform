package com.vidacruz.assistant.model.protocol;

/**
 * One section of a protocol of the official corpus, as found for a question.
 *
 * @param protocol   protocol code, such as {@code PROTO-001}
 * @param title      protocol title
 * @param section    section name, such as {@code Medidas iniciales}
 * @param content    text of the section
 * @param similarity how close the section is to the question, from 0 to 1
 */
public record ProtocolExcerpt(String protocol, String title, String section, String content, double similarity) {
}
