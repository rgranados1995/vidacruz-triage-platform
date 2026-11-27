package com.vidacruz.triage.archfixture.kafka;

import com.vidacruz.assistant.archfixture.ForeignService;

/** Fixture: the triage calling code of the assistant (breaks doesNotDependOnTheAssistant). */
public class CrossServicePublisher {

    public String publish() {
        return new ForeignService().answer();
    }
}
