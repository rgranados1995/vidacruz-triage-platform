package com.vidacruz.triage.archfixture.api;

import com.vidacruz.triage.archfixture.r2dbc.FixtureRepository;

/** Fixture: an entry point that skips the domain and calls a driven adapter (breaks layersKeepTheirDirection). */
public class LeakyHandler {

    private final FixtureRepository repository = new FixtureRepository();

    public String handle() {
        return repository.find();
    }
}
