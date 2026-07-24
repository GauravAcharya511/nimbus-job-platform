package com.gauravacharya.nimbus.job;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<JobResponse> submit(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = service.submit(request);
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id())).body(created);
    }

    @GetMapping
    public Page<JobResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) { return service.findById(id); }

    @DeleteMapping("/{id}")
    public JobResponse cancel(@PathVariable UUID id) { return service.cancel(id); }
}
