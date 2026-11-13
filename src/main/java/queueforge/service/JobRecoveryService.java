package queueforge.service;

import queueforge.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "queueforge.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(JobRecoveryService.class);
    private final JobRepository jobRepository;
    private final JobMetrics metrics;

    public JobRecoveryService(JobRepository jobRepository, JobMetrics metrics) {
        this.jobRepository = jobRepository;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${queueforge.worker.recovery-interval-ms:5000}")
    @Transactional
    public void recoverExpiredJobs() {
        int recovered = jobRepository.recoverExpiredLeases();
        if (recovered > 0) {
            metrics.jobsRecovered(recovered);
            log.warn("Recovered {} jobs with expired worker leases", recovered);
        }
    }
}
