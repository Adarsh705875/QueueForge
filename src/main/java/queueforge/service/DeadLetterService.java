package queueforge.service;

import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import queueforge.exception.JobNotFoundException;
import queueforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterService {
    private final JobRepository jobRepository;
    public DeadLetterService(JobRepository jobRepository) { this.jobRepository = jobRepository; }

    @Transactional
    public void replay(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getStatus() != JobStatus.DEAD) {
            throw new IllegalStateException("Only DEAD jobs can be replayed");
        }
        job.setStatus(JobStatus.QUEUED);
        job.setAttempts(0);
        job.setLastError(null);
        job.setNextRetryAt(null);
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setHeartbeatAt(null);
        job.setScheduledAt(null);
        jobRepository.save(job);
    }
}
