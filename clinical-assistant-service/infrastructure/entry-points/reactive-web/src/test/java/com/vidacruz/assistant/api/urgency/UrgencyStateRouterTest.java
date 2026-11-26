package com.vidacruz.assistant.api.urgency;

import com.vidacruz.assistant.api.config.SecurityHeadersConfig;
import com.vidacruz.assistant.api.error.ApiErrorHandler;
import com.vidacruz.assistant.api.error.ApiErrorWriter;
import com.vidacruz.assistant.api.security.ApiKeySecurityConfig;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.usecase.urgencystate.GetUrgencyStateUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {UrgencyStateRouter.class, UrgencyStateHandler.class, ApiErrorHandler.class,
        ApiErrorWriter.class, ApiKeySecurityConfig.class, SecurityHeadersConfig.class})
@TestPropertySource(properties = {"routes.urgency-state=/asistente/estado-urgencias",
        "errors.unavailable-retry-after=30s", "security.api-key=" + UrgencyStateRouterTest.KEY})
class UrgencyStateRouterTest {

    static final String KEY = "test-api-key-0001";
    private static final String STATE = "/asistente/estado-urgencias";
    private static final Instant UPDATED_AT = Instant.parse("2026-11-26T10:04:12.345678Z");

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private GetUrgencyStateUseCase getUrgencyState;

    @Test
    void shouldAnswerTheActivePatientsOfEveryLevelWithTheContractNames() {
        when(getUrgencyState.current()).thenReturn(Mono.just(new UrgencyState(
                Map.of(TriageLevel.CRITICAL, 1L, TriageLevel.URGENT, 2L, TriageLevel.NON_URGENT, 3L),
                Optional.of(UPDATED_AT))));

        get().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.totalActivos").isEqualTo(6)
                .jsonPath("$.porNivel.CRITICO").isEqualTo(1)
                .jsonPath("$.porNivel.URGENTE").isEqualTo(2)
                .jsonPath("$.porNivel.URGENTE_2").isEqualTo(0)
                .jsonPath("$.porNivel.NO_URGENTE").isEqualTo(3)
                .jsonPath("$.porNivel.NO_URGENTE_2").isEqualTo(0)
                .jsonPath("$.actualizadoEn").isEqualTo("2026-11-26T10:04:12.345678Z");
    }

    @Test
    void shouldAnswerAnEmptyStateBeforeTheFirstEvent() {
        when(getUrgencyState.current()).thenReturn(Mono.just(new UrgencyState(Map.of(), Optional.empty())));

        get().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalActivos").isEqualTo(0)
                .jsonPath("$.actualizadoEn").isEmpty();
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheStateCannotBeRead() {
        when(getUrgencyState.current()).thenReturn(Mono.error(
                new BusinessException(BusinessError.URGENCY_STATE_UNAVAILABLE, "")));

        get().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("AST-003")
                .jsonPath("$.title").isEqualTo("El estado de urgencias no está disponible en este momento")
                .jsonPath("$.detail").isEqualTo("Intente de nuevo en unos segundos; el registro de pacientes del "
                        + "triaje no se ve afectado");
    }

    @Test
    void shouldRequireAValidApiKey() {
        client.get().uri(STATE).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("AST-005");
        verifyNoInteractions(getUrgencyState);
    }

    @ParameterizedTest
    @CsvSource({"CRITICAL, CRITICO", "URGENT, URGENTE", "URGENT_2, URGENTE_2", "NON_URGENT, NO_URGENTE",
        "NON_URGENT_2, NO_URGENTE_2"})
    void shouldUseTheContractCodeOfEachLevel(TriageLevel level, String code) {
        assertThat(UrgencyStateHandler.code(level)).isEqualTo(code);
    }

    @Test
    void shouldListTheLevelsInTheirOrder() {
        var response = UrgencyStateHandler.toResponse(new UrgencyState(Map.of(TriageLevel.URGENT_2, 4L),
                Optional.of(UPDATED_AT)));

        assertThat(response.activeByLevel().keySet())
                .containsExactly("CRITICO", "URGENTE", "URGENTE_2", "NO_URGENTE", "NO_URGENTE_2");
        assertThat(response.totalActive()).isEqualTo(4);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    private WebTestClient.ResponseSpec get() {
        return client.get().uri(STATE).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange();
    }
}
