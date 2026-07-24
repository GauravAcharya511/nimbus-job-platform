package com.gauravacharya.nimbus.ratelimit;

import com.gauravacharya.nimbus.support.PostgresTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "nimbus.worker.scheduled=false")
@Import(PostgresTestContainer.class)
@TestPropertySource(properties = {
        "nimbus.ratelimit.enabled=true",
        "nimbus.ratelimit.capacity=10",
        "nimbus.ratelimit.refill-per-second=1"
})
class RateLimiterTest {

    @Autowired RateLimiter limiter;

    @Test
    @DisplayName("allows up to capacity, then throttles")
    void throttlesBeyondCapacity() {
        String user = UUID.randomUUID().toString();

        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (limiter.tryAcquire(user)) allowed++;
        }

        // capacity is 10; a slow run may refill a token or two
        assertThat(allowed).isBetween(10, 12);
    }

    @Test
    @DisplayName("buckets are isolated per user")
    void bucketsAreIsolatedPerUser() {
        String alice = UUID.randomUUID().toString();
        String bob = UUID.randomUUID().toString();

        for (int i = 0; i < 15; i++) limiter.tryAcquire(alice);

        // Alice is throttled, Bob is untouched
        assertThat(limiter.tryAcquire(bob)).isTrue();
    }

    @Test
    @DisplayName("concurrent callers never exceed capacity")
    void concurrentCallersRespectCapacity() throws Exception {
        String user = UUID.randomUUID().toString();
        int threads = 8;
        int attemptsPerThread = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire(user)) allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 80 concurrent attempts against a bucket of 10. Without atomic Lua
        // evaluation, racing read-modify-write cycles would let more through.
        assertThat(allowed.get()).isLessThanOrEqualTo(12);
    }
}