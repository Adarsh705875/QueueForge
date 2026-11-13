package queueforge.service;

import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import queueforge.exception.JobNotFoundException;
import queueforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobStateService {
    private final JobRepository jobRepository;
    public JobStateService(JobRepository jobRepository) { this.jobRepository = jobRepository; }

    @Transactional
    public JobStatus cancelJob(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getStatus() != JobStatus.QUEUED) {
            throw new IllegalStateException("Only QUEUED jobs can be cancelled");
        }
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
        return job.getStatus();
    }
}
