package queueforge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

public class JobRequest {
    @NotBlank(message = "Job type is required")
    @Size(max = 100, message = "Job type cannot exceed 100 characters")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,99}", message = "Job type must contain only letters, numbers, and underscores")
    private String jobType;

    @NotBlank(message = "Payload is required")
    @Size(max = 65536, message = "Payload cannot exceed 64 KB")
    private String payload;

    @Min(1) @Max(10)
    private Integer priority = 5;

    @Min(1) @Max(20)
    private Integer maxAttempts = 5;

    private LocalDateTime scheduledAt;

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
}
