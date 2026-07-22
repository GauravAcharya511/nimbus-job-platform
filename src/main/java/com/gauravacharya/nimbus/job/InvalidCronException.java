package com.gauravacharya.nimbus.job;

public class InvalidCronException extends RuntimeException {
    public InvalidCronException(String cron) {
        super("Invalid cron expression: " + cron);
    }
}
