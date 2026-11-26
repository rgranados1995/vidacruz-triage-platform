package com.vidacruz.assistant.redis;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.model.urgency.gateways.UrgencyStateRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/**
 * Projection of the emergency room in Redis: the latest state of each patient and one set of active patients per
 * level. Every change runs as one Lua script, so a reader never sees a patient counted twice or in no level.
 */
@Repository
@EnableConfigurationProperties(UrgencyStateProperties.class)
public class RedisUrgencyStateRepository implements UrgencyStateRepository {

    private static final long APPLIED = 1L;
    private static final String ACTIVE = "1";
    private static final String INACTIVE = "0";

    private final ReactiveStringRedisTemplate redis;
    private final String dischargedRetentionSeconds;
    private final RedisScript<Long> applyScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> readScript;

    /**
     * The scripts are read here, once, at startup: read lazily, the file would be read on a reactive thread the
     * first time a script runs.
     */
    public RedisUrgencyStateRepository(ReactiveStringRedisTemplate redis, UrgencyStateProperties properties)
            throws IOException {
        this.redis = redis;
        this.dischargedRetentionSeconds = String.valueOf(properties.dischargedRetention().toSeconds());
        this.applyScript = RedisScript.of(script("redis/apply-patient-state.lua"), Long.class);
        this.readScript = RedisScript.of(script("redis/read-urgency-state.lua"), List.class);
    }

    private static String script(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Boolean> apply(PatientSnapshot snapshot) {
        var keys = new ArrayList<String>();
        keys.add(UrgencyKeys.patient(snapshot.patientId()));
        keys.add(UrgencyKeys.UPDATED_AT);
        keys.addAll(UrgencyKeys.activeOfEveryLevel());
        keys.add(UrgencyKeys.active(snapshot.level()));
        var arguments = List.of(snapshot.patientId().toString(), snapshot.level().name(), snapshot.status().name(),
                String.valueOf(toMicros(snapshot.occurredAt())), snapshot.isActive() ? ACTIVE : INACTIVE,
                dischargedRetentionSeconds);
        return redis.execute(applyScript, keys, arguments)
                .next()
                .map(result -> result == APPLIED)
                .onErrorMap(RedisUrgencyStateRepository::unavailable);
    }

    @Override
    public Mono<UrgencyState> current() {
        var keys = new ArrayList<>(UrgencyKeys.activeOfEveryLevel());
        keys.add(UrgencyKeys.UPDATED_AT);
        return redis.execute(readScript, keys, List.of())
                .next()
                .map(RedisUrgencyStateRepository::toState)
                .onErrorMap(RedisUrgencyStateRepository::unavailable);
    }

    @Override
    public Mono<Boolean> isInitialized() {
        return redis.hasKey(UrgencyKeys.INITIALIZED).onErrorMap(RedisUrgencyStateRepository::unavailable);
    }

    @Override
    public Mono<Void> markInitialized() {
        return redis.opsForValue().set(UrgencyKeys.INITIALIZED, Instant.now().toString())
                .then()
                .onErrorMap(RedisUrgencyStateRepository::unavailable);
    }

    /** The counts of the levels in their order, then the time of the latest event ('' before any event). */
    static UrgencyState toState(List<?> result) {
        var counts = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        var levels = TriageLevel.values();
        for (var index = 0; index < levels.length; index++) {
            counts.put(levels[index], ((Number) result.get(index)).longValue());
        }
        var updatedAt = String.valueOf(result.get(levels.length));
        return new UrgencyState(counts, updatedAt.isEmpty()
                ? Optional.empty()
                : Optional.of(Instant.EPOCH.plus(Long.parseLong(updatedAt), ChronoUnit.MICROS)));
    }

    static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static Throwable unavailable(Throwable error) {
        return error instanceof BusinessException
                ? error
                : new BusinessException(BusinessError.URGENCY_STATE_UNAVAILABLE, "", error);
    }
}
