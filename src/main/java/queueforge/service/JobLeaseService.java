package queueforge.service;

import queueforge.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLeaseService {
    private final JobRepository jobRepository;
    private final int leaseDurationSeconds;

    public JobLeaseService(JobRepository jobRepository,
            @Value("${queueforge.worker.lease-duration-seconds:30}") int leaseDurationSeconds) {
        this.jobRepository = jobRepository;
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    @Transactional
    public void heartbeat(Long jobId, String workerId, long leaseVersion) {
        int updated = jobRepository.extendLease(jobId, workerId, leaseVersion, leaseDurationSeconds);
        if (updated != 1) throw new IllegalStateException("Worker lease is no longer valid for job " + jobId);
    }
}
