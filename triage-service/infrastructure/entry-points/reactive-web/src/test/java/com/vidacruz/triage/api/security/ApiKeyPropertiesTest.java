package com.vidacruz.triage.api.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyPropertiesTest {

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {"                    ", "short-key-15chr"})
    void shouldNotStartWithAMissingOrShortKey(String apiKey) {
        assertThatThrownBy(() -> new ApiKeyProperties(apiKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("security.api-key must have at least 16 characters");
    }

    @Test
    void shouldAcceptAKeyOfSixteenCharacters() {
        assertThat(new ApiKeyProperties("sixteen-chars-ok").apiKey()).isEqualTo("sixteen-chars-ok");
    }
}
