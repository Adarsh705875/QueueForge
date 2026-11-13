package queueforge.service;

import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import queueforge.exception.JobNotFoundException;
import queueforge.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class JobClaimService {
    private final JobRepository jobRepository;
    private final int leaseDurationSeconds;

    public JobClaimService(JobRepository jobRepository,
            @Value("${queueforge.worker.lease-duration-seconds:30}") int leaseDurationSeconds) {
        this.jobRepository = jobRepository;
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    @Transactional
    public Optional<Job> claimNextJob(String workerId) {
        Optional<Long> nextId = jobRepository.findNextJobIdForUpdate();
        if (nextId.isEmpty()) return Optional.empty();
        Job job = jobRepository.findById(nextId.get())
                .orElseThrow(() -> new JobNotFoundException(nextId.get()));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        job.setStatus(JobStatus.PROCESSING);
        job.incrementAttempts();
        job.incrementLeaseVersion();
        job.setNextRetryAt(null);
        job.setLeaseOwner(workerId);
        job.setHeartbeatAt(now);
        job.setLeaseExpiresAt(now.plusSeconds(leaseDurationSeconds));
        return Optional.of(jobRepository.saveAndFlush(job));
    }
}
