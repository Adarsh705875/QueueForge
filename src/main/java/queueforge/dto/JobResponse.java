package queueforge.dto;

import queueforge.entity.JobStatus;
import java.time.LocalDateTime;

public class JobResponse {
    private final Long id;
    private final String jobType;
    private final String payload;
    private final JobStatus status;
    private final Integer priority;
    private final Integer attempts;
    private final Integer maxAttempts;
    private final LocalDateTime createdAt;
    private final LocalDateTime scheduledAt;
    private final LocalDateTime nextRetryAt;
    private final String lastError;

    public JobResponse(Long id, String jobType, String payload, JobStatus status,
                       Integer priority, Integer attempts, Integer maxAttempts,
                       LocalDateTime createdAt, LocalDateTime scheduledAt,
                       LocalDateTime nextRetryAt, String lastError) {
        this.id = id; this.jobType = jobType; this.payload = payload;
        this.status = status; this.priority = priority; this.attempts = attempts;
        this.maxAttempts = maxAttempts; this.createdAt = createdAt;
        this.scheduledAt = scheduledAt; this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
    }
    public Long getId() { return id; }
    public String getJobType() { return jobType; }
    public String getPayload() { return payload; }
    public JobStatus getStatus() { return status; }
    public Integer getPriority() { return priority; }
    public Integer getAttempts() { return attempts; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public String getLastError() { return lastError; }
}
