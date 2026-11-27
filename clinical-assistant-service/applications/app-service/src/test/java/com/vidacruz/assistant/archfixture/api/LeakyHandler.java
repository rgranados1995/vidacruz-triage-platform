package com.vidacruz.assistant.archfixture.api;

import com.vidacruz.assistant.archfixture.redis.FixtureStore;

/** Fixture: an entry point that skips the domain and calls a driven adapter (breaks layersKeepTheirDirection). */
public class LeakyHandler {

    private final FixtureStore store = new FixtureStore();

    public long handle() {
        return store.count();
    }
}
