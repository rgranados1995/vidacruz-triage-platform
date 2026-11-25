package com.vidacruz.triage.model.triage;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chief complaints that are administrative (RN-01, level 5). A complaint matches only when it is the same phrase
 * after ignoring case, accents and extra spaces: a clinical complaint that merely mentions an administrative word
 * ("fiebre tras vacunación") keeps the more urgent level, which is the safe side for the patient.
 */
public class AdministrativeComplaints {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Set<String> phrases = new HashSet<>();

    public AdministrativeComplaints(Collection<String> phrases) {
        for (var phrase : phrases) {
            this.phrases.add(normalize(phrase));
        }
    }

    public boolean contains(String complaint) {
        return complaint != null && phrases.contains(normalize(complaint));
    }

    private static String normalize(String text) {
        var withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutAccents.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
