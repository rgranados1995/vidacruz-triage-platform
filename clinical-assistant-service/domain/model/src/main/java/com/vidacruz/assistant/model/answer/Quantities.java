package com.vidacruz.assistant.model.answer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The numbers of a clinical text, alone and with their unit ({@code 20}, {@code 20 ml/kg}). A dose, a time or a
 * threshold is what hurts a patient when invented, so a sentence of the answer may only contain quantities its
 * excerpts contain: {@code 20 ml/kg} is supported by an excerpt that says {@code 20 ml/kg}, never by one that says
 * {@code 20 mg} or {@code 30 ml/kg}. A decimal comma and a decimal point are the same, and {@code 4 horas} is
 * {@code 4 h}.
 */
final class Quantities {

    // A number not glued to a word (the 1 of E1 is not a quantity) and the word right after it, a unit or not
    private static final Pattern QUANTITY =
            Pattern.compile("(?<![\\p{L}\\d])(?<number>\\d+(?:[.,]\\d+)?)(?:\\s*(?<unit>%|\\p{L}+(?:/\\p{L}+)?))?");
    private static final Set<String> UNITS = Set.of("%", "mmhg", "mg", "mcg", "g", "kg", "ml", "l", "ui", "meq",
            "mmol", "lpm", "rpm", "h", "min", "s", "mg/kg", "mcg/kg", "ml/kg", "ui/kg", "meq/kg", "ml/h", "mg/h",
            "mcg/min", "mmol/l", "mg/dl");
    private static final Map<String, String> SAME_UNIT = Map.of("horas", "h", "hora", "h", "minutos", "min",
            "minuto", "min", "segundos", "s", "µg", "mcg");

    private Quantities() {
    }

    /** Every number of the text, and every number with its unit. */
    static Set<String> inText(String text) {
        var quantities = new HashSet<String>();
        var matcher = QUANTITY.matcher(text);
        while (matcher.find()) {
            var number = matcher.group("number").replace(',', '.');
            quantities.add(number);
            var unit = unit(matcher.group("unit"));
            if (UNITS.contains(unit)) {
                quantities.add(number + " " + unit);
            }
        }
        return quantities;
    }

    private static String unit(String word) {
        var unit = word == null ? "" : word.toLowerCase(Locale.ROOT);
        return SAME_UNIT.getOrDefault(unit, unit);
    }
}
