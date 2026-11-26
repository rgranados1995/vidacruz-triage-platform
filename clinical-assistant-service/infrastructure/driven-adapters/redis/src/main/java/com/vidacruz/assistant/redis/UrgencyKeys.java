package com.vidacruz.assistant.redis;

import com.vidacruz.assistant.model.urgency.TriageLevel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keys of the projection. They share the hash tag {@code {urgency}}, so they live in the same slot and the scripts
 * that touch several of them also work on a Redis cluster.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UrgencyKeys {

    private static final String PREFIX = "{urgency}:";
    static final String UPDATED_AT = PREFIX + "updated-at";
    static final String INITIALIZED = PREFIX + "initialized";

    static String patient(UUID patientId) {
        return PREFIX + "patient:" + patientId;
    }

    static String active(TriageLevel level) {
        return PREFIX + "active:" + level.name();
    }

    /** The active set of every level, in the order of the levels. */
    static List<String> activeOfEveryLevel() {
        var keys = new ArrayList<String>();
        for (var level : TriageLevel.values()) {
            keys.add(active(level));
        }
        return keys;
    }
}
