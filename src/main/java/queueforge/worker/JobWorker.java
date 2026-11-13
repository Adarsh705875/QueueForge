package queueforge.worker;

import queueforge.entity.Job;
import queueforge.service.JobClaimService;
import queueforge.service.JobExecutionService;
import queueforge.service.JobLeaseService;
import queueforge.service.RetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final JobClaimService claimService;
    private final JobExecutionService executionService;
    private final JobLeaseService leaseService;
    private final RetryService retryService;
    private final List<JobHandler> handlers;
    private final ThreadPoolTaskScheduler heartbeatScheduler;
    private final long heartbeatIntervalMs;

    public JobWorker(JobClaimService claimService, JobExecutionService executionService,
                     JobLeaseService leaseService, RetryService retryService,
                     List<JobHandler> handlers,
                     @Qualifier("leaseHeartbeatScheduler") ThreadPoolTaskScheduler heartbeatScheduler,
                     @Value("${queueforge.worker.heartbeat-interval-ms:10000}") long heartbeatIntervalMs) {
        this.claimService = claimService;
        this.executionService = executionService;
        this.leaseService = leaseService;
        this.retryService = retryService;
        this.handlers = handlers;
        this.heartbeatScheduler = heartbeatScheduler;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public void processNextJob() {
        String workerId = Thread.currentThread().getName() + "-" + UUID.randomUUID();
        Optional<Job> claimed = claimService.claimNextJob(workerId);
        if (claimed.isEmpty()) return;
        Job job = claimed.get();
        long leaseVersion = job.getLeaseVersion();
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                leaseService.heartbeat(job.getId(), workerId, leaseVersion);
            } catch (Exception ex) {
                leaseLost.set(true);
                log.warn("Heartbeat failed for job {}: {}", job.getId(), ex.getMessage());
            }
        }, Instant.now().plusMillis(heartbeatIntervalMs), Duration.ofMillis(heartbeatIntervalMs));

        try {
            JobHandler handler = handlers.stream().filter(h -> h.supports(job.getJobType()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "No handler found for job type: " + job.getJobType()));
            log.info("Worker {} claimed job {} at lease version {}", workerId, job.getId(), leaseVersion);
            handler.execute(job);
            if (leaseLost.get()) throw new IllegalStateException("Worker lost its lease during execution");
            executionService.markCompleted(job.getId(), workerId, leaseVersion);
            log.info("Job {} completed", job.getId());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Worker interrupted while processing job {}; lease will expire and recovery will retry it", job.getId());
        } catch (Exception ex) {
            log.error("Job {} failed: {}", job.getId(), ex.getMessage());
            try {
                retryService.recordFailure(job.getId(), ex.getMessage(), workerId, leaseVersion, job.getAttempts());
            } catch (Exception stateError) {
                log.warn("Could not update job {} because its lease may have expired: {}", job.getId(), stateError.getMessage());
            }
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
        }
    }
}
