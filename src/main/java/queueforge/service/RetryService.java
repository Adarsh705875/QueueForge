package queueforge.service;

import queueforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RetryService {
    private final JobRepository jobRepository;
    private final JobMetrics metrics;

    public RetryService(JobRepository jobRepository, JobMetrics metrics) {
        this.jobRepository = jobRepository;
        this.metrics = metrics;
    }

    @Transactional
    public void recordFailure(Long id, String error, String workerId, long leaseVersion, int attemptNumber) {
        long base = Math.min(300, 1L << Math.min(Math.max(attemptNumber, 1), 8));
        int jitter = ThreadLocalRandom.current().nextInt((int) (base / 2) + 1);
        int delay = (int) base + jitter;
        String safeError = error == null ? "Unknown execution error" : error.substring(0, Math.min(error.length(), 4000));
        int updated = jobRepository.failIfOwned(id, workerId, leaseVersion, safeError, delay);
        if (updated != 1) throw new IllegalStateException("Cannot record failure: lease expired or ownership lost for job " + id);
        metrics.jobFailed();
    }
}
