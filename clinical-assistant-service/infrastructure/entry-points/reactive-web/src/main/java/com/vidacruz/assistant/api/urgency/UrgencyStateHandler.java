package com.vidacruz.assistant.api.urgency;

import com.vidacruz.assistant.api.urgency.dto.UrgencyStateResponse;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.usecase.urgencystate.GetUrgencyStateUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

/**
 * HTTP side of RF-04 part B: the projection as it is now, with the levels as the triage contract names them.
 */
@Component
@RequiredArgsConstructor
public class UrgencyStateHandler {

    private final GetUrgencyStateUseCase getUrgencyState;

    public Mono<ServerResponse> current(ServerRequest request) {
        return getUrgencyState.current()
                .flatMap(state -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(toResponse(state)));
    }

    static UrgencyStateResponse toResponse(UrgencyState state) {
        var byLevel = new LinkedHashMap<String, Long>();
        for (var level : TriageLevel.values()) {
            byLevel.put(code(level), state.activeByLevel().get(level));
        }
        return new UrgencyStateResponse(state.totalActive(), byLevel, state.updatedAt().orElse(null));
    }

    static String code(TriageLevel level) {
        return switch (level) {
            case CRITICAL -> "CRITICO";
            case URGENT -> "URGENTE";
            case URGENT_2 -> "URGENTE_2";
            case NON_URGENT -> "NO_URGENTE";
            case NON_URGENT_2 -> "NO_URGENTE_2";
        };
    }
}
