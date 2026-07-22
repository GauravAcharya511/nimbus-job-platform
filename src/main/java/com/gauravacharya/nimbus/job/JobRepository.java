package com.gauravacharya.nimbus.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Page<Job> findByUserId(UUID userId, Pageable pageable);
    Optional<Job> findByIdAndUserId(UUID id, UUID userId);
}