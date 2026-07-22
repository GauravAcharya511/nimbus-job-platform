package com.gauravacharya.nimbus.worker;

import com.gauravacharya.nimbus.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reference executor used for demos and tests. */
@Component
public class EchoJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(EchoJobExecutor.class);

    @Override
    public String type() { return "echo"; }

    @Override
    public void execute(Job job) {
        log.info("echo job {} payload={}", job.getId(), job.getPayload());
    }
}
