package com.vidacruz.triage.archfixture;

/** Fixture: a class of the other service, only for the rule that forbids depending on it. */
public class ForeignService {

    public String register() {
        return "patient";
    }
}
