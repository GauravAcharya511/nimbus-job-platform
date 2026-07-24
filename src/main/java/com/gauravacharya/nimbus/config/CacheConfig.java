package com.gauravacharya.nimbus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gauravacharya.nimbus.job.JobResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    /**
     * Short TTL by design. Terminal jobs never change, so a stale read is impossible
     * once a job reaches SUCCEEDED or FAILED; a running job may be briefly stale,
     * which is an acceptable trade for removing read load from the database.
     *
     * The serializer is bound to JobResponse rather than using polymorphic typing.
     * JobResponse is a record, and therefore final, so default typing would not
     * emit the type hint needed to reconstruct it on read-back. Binding the type
     * directly avoids the problem and keeps the cached payload smaller.
     */
    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, JobResponse.class)));
    }
}
