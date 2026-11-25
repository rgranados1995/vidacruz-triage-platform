package com.vidacruz.triage.kafka;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import com.vidacruz.triage.model.outbox.gateways.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Publishes the outbox events to the triage topic with reactor-kafka: the send never blocks the caller and completes
 * when the broker acknowledges the record. The key is the patient id, so the events of a patient keep their order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    static final String CONTENT_TYPE_HEADER = "content-type";
    static final String CLOUD_EVENTS_JSON = "application/cloudevents+json; charset=UTF-8";

    private final ReactiveKafkaProducerTemplate<String, String> producer;
    private final TriageEventsTopicProperties topic;

    @Override
    public Mono<Void> publish(OutboxMessage message) {
        var contentType = new RecordHeader(CONTENT_TYPE_HEADER, CLOUD_EVENTS_JSON.getBytes(StandardCharsets.UTF_8));
        var producerRecord = new ProducerRecord<>(topic.name(), null, message.key(), message.payload(),
                List.of(contentType));
        return producer.send(producerRecord)
                .flatMap(result -> Mono.justOrEmpty(result.exception()).flatMap(Mono::<Void>error)
                        .thenReturn(result))
                .doOnNext(result -> log.atDebug()
                        .addKeyValue("eventId", message.eventId())
                        .addKeyValue("eventType", message.type())
                        .addKeyValue("partition", result.recordMetadata().partition())
                        .addKeyValue("offset", result.recordMetadata().offset())
                        .log("Event published"))
                .then();
    }
}
