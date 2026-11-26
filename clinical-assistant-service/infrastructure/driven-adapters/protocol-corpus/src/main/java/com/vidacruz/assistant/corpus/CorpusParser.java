package com.vidacruz.assistant.corpus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a protocol written in the corpus format:
 * <pre>
 * # PROTO-001 — Title of the protocol
 *
 * ## Section name
 * Text of the section, on one or more lines.
 * </pre>
 * A malformed protocol stops the startup: a corpus that cannot be read must never be served half loaded.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusParser {

    private static final Pattern HEADER = Pattern.compile("^# (?<protocol>PROTO-\\d{3}) — (?<title>.+)$");
    private static final String SECTION_PREFIX = "## ";

    static List<CorpusSection> parse(String source, String markdown) {
        var lines = markdown.strip().lines().toList();
        var header = HEADER.matcher(lines.isEmpty() ? "" : lines.getFirst().strip());
        if (!header.matches()) {
            throw new IllegalStateException(source + " must start with '# PROTO-NNN — Title'");
        }
        var protocol = new Protocol(source, header.group("protocol"), header.group("title").strip());
        var sections = new ArrayList<CorpusSection>();
        var draft = new SectionDraft(null);
        for (var line : lines.subList(1, lines.size())) {
            if (line.startsWith(SECTION_PREFIX)) {
                draft.addTo(protocol, sections);
                draft = new SectionDraft(line.substring(SECTION_PREFIX.length()).strip());
            } else if (!line.isBlank()) {
                draft.append(line.strip());
            }
        }
        draft.addTo(protocol, sections);
        if (sections.isEmpty()) {
            throw new IllegalStateException(source + " has no sections");
        }
        return sections;
    }

    private record Protocol(String source, String code, String title) {
    }

    /** A section being read; the text before the first section heading has no name and is ignored. */
    private static final class SectionDraft {

        private final String name;
        private final StringBuilder content = new StringBuilder();

        private SectionDraft(String name) {
            this.name = name;
        }

        private void append(String line) {
            content.append(content.isEmpty() ? "" : " ").append(line);
        }

        private void addTo(Protocol protocol, List<CorpusSection> sections) {
            if (name == null) {
                return;
            }
            if (name.isEmpty() || content.isEmpty()) {
                throw new IllegalStateException(protocol.source() + " has a section without name or text: '" + name
                        + "'");
            }
            sections.add(new CorpusSection(protocol.code(), protocol.title(), name, content.toString()));
        }
    }
}
