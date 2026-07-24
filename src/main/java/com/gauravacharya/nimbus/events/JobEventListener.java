package com.gauravacharya.nimbus.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reference consumer for the job lifecycle topic.
 *
 * Demonstrates that events are independently consumable: this runs in the same
 * process for convenience, but nothing about it depends on that — a separate
 * service could subscribe to the same topic without touching the job database.
 */
@Component
@ConditionalOnProperty(name = "nimbus.events.enabled", havingValue = "true", matchIfMissing = true)
public class JobEventListener {

    private static final Logger log = LoggerFactory.getLogger(JobEventListener.class);

    private final Map<JobEventType, AtomicLong> counts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${nimbus.events.topic:nimbus.job.events}", groupId = "nimbus-audit")
    public void onEvent(JobEvent event) {
        counts.computeIfAbsent(event.eventType(), k -> new AtomicLong()).incrementAndGet();
        log.debug("job {} -> {}", event.jobId(), event.eventType());
    }

    /** Snapshot of events observed since startup, exposed for verification. */
    public Map<JobEventType, Long> counts() {
        return counts.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }
}
