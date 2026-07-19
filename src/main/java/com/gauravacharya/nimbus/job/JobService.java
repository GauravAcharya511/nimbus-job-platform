package com.gauravacharya.nimbus.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository repository;

    public JobService(JobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public JobResponse submit(CreateJobRequest request) {
        Job job = new Job();
        job.setType(request.type());
        job.setPayload(request.payload());
        job.setStatus(JobStatus.PENDING);
        return JobResponse.from(repository.save(job));
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(JobResponse::from);
    }

    @Transactional(readOnly = true)
    public JobResponse findById(UUID id) {
        return repository.findById(id)
                .map(JobResponse::from)
                .orElseThrow(() -> new JobNotFoundException(id));
    }
}
