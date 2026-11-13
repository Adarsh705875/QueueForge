package queueforge.service;

import queueforge.dto.JobCreateResult;
import queueforge.dto.JobRequest;
import queueforge.dto.JobResponse;
import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import queueforge.exception.IdempotencyConflictException;
import queueforge.exception.JobNotFoundException;
import queueforge.repository.JobRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.Locale;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final JobCreationService creationService;
    private final JobMetrics metrics;
    private final boolean idempotencyRequired;

    public JobService(JobRepository jobRepository, JobCreationService creationService, JobMetrics metrics,
                      @Value("${queueforge.idempotency.required:false}") boolean idempotencyRequired) {
        this.jobRepository = jobRepository;
        this.creationService = creationService;
        this.metrics = metrics;
        this.idempotencyRequired = idempotencyRequired;
    }

    public JobCreateResult createJob(JobRequest request, String idempotencyKey) {
        String jobType = request.getJobType().trim().toUpperCase(Locale.ROOT);
        String payload = request.getPayload();
        int priority = request.getPriority() == null ? 5 : request.getPriority();
        int maxAttempts = request.getMaxAttempts() == null ? 5 : request.getMaxAttempts();
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (idempotencyRequired && key == null) throw new IllegalArgumentException("Idempotency-Key header is required");
        if (key != null && key.length() > 255) throw new IllegalArgumentException("Idempotency-Key cannot exceed 255 characters");
        String hash = IdempotencyHasher.hash(jobType, payload, priority, maxAttempts, request.getScheduledAt());

        if (key != null) {
            var existing = jobRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) return existingResult(existing.get(), hash);
        }

        Job job = new Job();
        job.setJobType(jobType);
        job.setPayload(payload);
        job.setPriority(priority);
        job.setMaxAttempts(maxAttempts);
        job.setScheduledAt(request.getScheduledAt());
        job.setIdempotencyKey(key);
        job.setRequestHash(hash);

        try {
            Long id = creationService.create(job);
            metrics.jobCreated();
            return new JobCreateResult(convertToResponse(jobRepository.findById(id)
                    .orElseThrow(() -> new JobNotFoundException(id))), false);
        } catch (DataIntegrityViolationException race) {
            if (key == null) throw race;
            var winner = jobRepository.findByIdempotencyKey(key)
                    .orElseThrow(() -> race);
            return existingResult(winner, hash);
        }
    }

    private JobCreateResult existingResult(Job existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException("This Idempotency-Key was already used with a different request");
        }
        return new JobCreateResult(convertToResponse(existing), true);
    }

    public Page<JobResponse> getAllJobs(Pageable pageable) {
        return jobRepository.findAll(pageable).map(this::convertToResponse);
    }

    public Page<JobResponse> getDeadJobs(Pageable pageable) {
        return jobRepository.findAllByStatus(JobStatus.DEAD, pageable).map(this::convertToResponse);
    }

    public JobResponse getJobById(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        return convertToResponse(job);
    }

    public JobResponse convertToResponse(Job job) {
        return new JobResponse(job.getId(), job.getJobType(), job.getPayload(), job.getStatus(),
                job.getPriority(), job.getAttempts(), job.getMaxAttempts(), job.getCreatedAt(),
                job.getScheduledAt(), job.getNextRetryAt(), job.getLastError());
    }
}
