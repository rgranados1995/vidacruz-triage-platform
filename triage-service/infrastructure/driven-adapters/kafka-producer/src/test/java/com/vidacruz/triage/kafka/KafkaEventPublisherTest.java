package com.vidacruz.triage.kafka;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    private static final TriageEventsTopicProperties TOPIC = new TriageEventsTopicProperties("triaje.eventos", 3,
            (short) 1);

    @Mock
    private ReactiveKafkaProducerTemplate<String, String> producer;

    @Test
    @SuppressWarnings("unchecked")
    void shouldFailWhenTheBrokerRejectsTheRecord() {
        var rejected = new TopicAuthorizationException("not authorized");
        SenderResult<Void> result = mock(SenderResult.class);
        when(result.exception()).thenReturn(rejected);
        when(producer.send(any(ProducerRecord.class))).thenReturn(Mono.just(result));

        StepVerifier.create(new KafkaEventPublisher(producer, TOPIC).publish(message()))
                .expectErrorMatches(error -> error == rejected)
                .verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendToTheConfiguredTopicWithThePatientAsKey() {
        var message = message();
        when(producer.send(any(ProducerRecord.class))).thenReturn(Mono.error(new IllegalStateException("closed")));

        StepVerifier.create(new KafkaEventPublisher(producer, TOPIC).publish(message)).expectError().verify();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("triaje.eventos");
        assertThat(sent.getValue().key()).isEqualTo(message.key());
        assertThat(sent.getValue().value()).isEqualTo(message.payload());
        assertThat(sent.getValue().partition()).isNull();
        // Without a trace the record carries only its content type
        assertThat(sent.getValue().headers().lastHeader("traceparent")).isNull();
        assertThat(header(sent.getValue(), "content-type")).isEqualTo("application/cloudevents+json; charset=UTF-8");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandTheTraceContextOfTheChangeToTheConsumers() {
        var traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        var original = message();
        var traced = new OutboxMessage(original.eventId(), original.key(), original.type(), original.payload(),
                original.occurredAt(), traceParent);
        when(producer.send(any(ProducerRecord.class))).thenReturn(Mono.error(new IllegalStateException("closed")));

        StepVerifier.create(new KafkaEventPublisher(producer, TOPIC).publish(traced)).expectError().verify();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(sent.capture());
        assertThat(header(sent.getValue(), "traceparent")).isEqualTo(traceParent);
        assertThat(header(sent.getValue(), "content-type")).isEqualTo("application/cloudevents+json; charset=UTF-8");
    }

    private static String header(ProducerRecord<String, String> sent, String name) {
        return new String(sent.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void shouldDeclareTheTopicWithItsPartitionsAndReplicas() {
        var topic = new KafkaProducerConfig().triageEventsTopic(TOPIC);

        assertThat(topic.name()).isEqualTo("triaje.eventos");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void shouldBuildTheProducerFromTheSpringKafkaSettings() {
        var kafkaProperties = new KafkaProperties();
        kafkaProperties.getProducer().setKeySerializer(StringSerializer.class);
        kafkaProperties.getProducer().setValueSerializer(StringSerializer.class);

        var template = new KafkaProducerConfig().triageEventsProducer(kafkaProperties, new DefaultSslBundleRegistry());

        assertThat(template).isNotNull();
        template.close();
    }

    private static OutboxMessage message() {
        return new OutboxMessage(UUID.randomUUID(), UUID.randomUUID().toString(), "paciente_registrado", "{}",
                Instant.now(), null);
    }
}
