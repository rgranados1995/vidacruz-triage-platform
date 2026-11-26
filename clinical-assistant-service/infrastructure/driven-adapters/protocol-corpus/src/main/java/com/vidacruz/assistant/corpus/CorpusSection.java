package com.vidacruz.assistant.corpus;

/**
 * One section of a protocol of the corpus, the unit that is searched and cited.
 *
 * @param protocol protocol code, such as {@code PROTO-001}
 * @param title    protocol title
 * @param section  section name
 * @param content  text of the section
 */
record CorpusSection(String protocol, String title, String section, String content) {

    /** Text that is turned into an embedding: the title and the section name give the content its context. */
    String searchableText() {
        return title + ". " + section + ": " + content;
    }
}
