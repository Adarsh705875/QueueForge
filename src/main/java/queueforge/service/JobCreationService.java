package queueforge.service;

import queueforge.entity.Job;
import queueforge.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobCreationService {
    private final JobRepository jobRepository;
    public JobCreationService(JobRepository jobRepository) { this.jobRepository = jobRepository; }

    @Transactional
    public Long create(Job job) {
        return jobRepository.saveAndFlush(job).getId();
    }
}
