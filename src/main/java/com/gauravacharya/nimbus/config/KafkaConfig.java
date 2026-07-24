package com.gauravacharya.nimbus.config;

import com.gauravacharya.nimbus.events.JobEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring.
 *
 * Declared explicitly rather than relying on auto-configuration: Spring Boot 4
 * splits auto-config into per-technology starters, so having spring-kafka on the
 * classpath does not by itself produce a KafkaTemplate bean.
 */
@Configuration
public class KafkaConfig {

    @Bean
    ProducerFactory<String, JobEvent> jobEventProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9094}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Never let an unreachable broker block a request thread indefinitely.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, JobEvent> jobEventKafkaTemplate(ProducerFactory<String, JobEvent> factory) {
        return new KafkaTemplate<>(factory);
    }

    /** Created on startup so the topic exists with known settings rather than auto-created. */
    @Bean
    NewTopic jobEventsTopic(@Value("${nimbus.events.topic:nimbus.job.events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
