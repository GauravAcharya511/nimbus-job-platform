package com.gauravacharya.nimbus.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobEventPublisher.class);

    private final KafkaTemplate<String, JobEvent> kafka;
    private final boolean enabled;
    private final String topic;

    public JobEventPublisher(KafkaTemplate<String, JobEvent> kafka,
                             @Value("${nimbus.events.enabled:true}") boolean enabled,
                             @Value("${nimbus.events.topic:nimbus.job.events}") String topic) {
        this.kafka = kafka;
        this.enabled = enabled;
        this.topic = topic;
    }

    /**
     * Publishes keyed by job id, so all events for one job land on the same partition
     * and are therefore consumed in order. Publishing is best-effort: a broker outage
     * degrades observability, and must not fail the job it is reporting on.
     */
    public void publish(JobEvent event) {
        if (!enabled) return;
        try {
            kafka.send(topic, event.jobId().toString(), event)
                 .whenComplete((result, ex) -> {
                     if (ex != null) {
                         log.warn("failed to publish {} for job {}: {}",
                                 event.eventType(), event.jobId(), ex.getMessage());
                     }
                 });
        } catch (Exception e) {
            log.warn("event publish rejected for job {}: {}", event.jobId(), e.getMessage());
        }
    }
}
