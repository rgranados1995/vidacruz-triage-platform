package com.vidacruz.assistant.archfixture.claude;

import com.vidacruz.triage.archfixture.ForeignService;

/** Fixture: the assistant calling code of the triage (breaks doesNotDependOnTheTriage). */
public class CrossServiceClient {

    public String call() {
        return new ForeignService().register();
    }
}
