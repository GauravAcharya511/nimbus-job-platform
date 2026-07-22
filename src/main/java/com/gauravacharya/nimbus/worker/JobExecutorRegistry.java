package com.gauravacharya.nimbus.worker;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobExecutorRegistry {

    private final Map<String, JobExecutor> executors;

    public JobExecutorRegistry(List<JobExecutor> discovered) {
        this.executors = discovered.stream()
                .collect(Collectors.toMap(JobExecutor::type, Function.identity()));
    }

    public Optional<JobExecutor> forType(String type) {
        return Optional.ofNullable(executors.get(type));
    }
}
