package queueforge.service;

import queueforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExecutionService {
    private final JobRepository jobRepository;
    private final JobMetrics metrics;

    public JobExecutionService(JobRepository jobRepository, JobMetrics metrics) {
        this.jobRepository = jobRepository;
        this.metrics = metrics;
    }

    @Transactional
    public void markCompleted(Long id, String workerId, long leaseVersion) {
        int updated = jobRepository.completeIfOwned(id, workerId, leaseVersion);
        if (updated != 1) {
            throw new IllegalStateException("Job lease expired or worker no longer owns job " + id);
        }
        metrics.jobCompleted();
    }
}
