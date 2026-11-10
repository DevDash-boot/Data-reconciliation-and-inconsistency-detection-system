package com.example.recon.common.exception;

public class JobFailedException extends RuntimeException {

    private final Long jobId;

    public JobFailedException(Long jobId, String reason, Throwable cause) {
        super("Job #" + jobId + " 실행 실패: " + reason, cause);
        this.jobId = jobId;
    }

    public Long getJobId() {
        return jobId;
    }
}
