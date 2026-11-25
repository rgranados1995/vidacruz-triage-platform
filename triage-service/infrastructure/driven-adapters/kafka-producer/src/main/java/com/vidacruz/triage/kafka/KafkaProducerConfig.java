package com.vidacruz.triage.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.kafka.sender.SenderOptions;

/**
 * Reactive Kafka producer built from the {@code spring.kafka.*} settings, and the topic of the triage events, which
 * Spring Kafka creates at startup when it does not exist (the broker does not create topics on its own).
 */
@Configuration
@EnableConfigurationProperties(TriageEventsTopicProperties.class)
public class KafkaProducerConfig {

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> triageEventsProducer(KafkaProperties kafkaProperties,
                                                                             SslBundles sslBundles) {
        var senderOptions = SenderOptions.<String, String>create(kafkaProperties.buildProducerProperties(sslBundles));
        return new ReactiveKafkaProducerTemplate<>(senderOptions);
    }

    @Bean
    public NewTopic triageEventsTopic(TriageEventsTopicProperties topic) {
        return TopicBuilder.name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .build();
    }
}
