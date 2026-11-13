package queueforge.controller;

import queueforge.dto.JobCreateResult;
import queueforge.dto.JobRequest;
import queueforge.dto.JobResponse;
import queueforge.service.DeadLetterService;
import queueforge.service.JobService;
import queueforge.service.JobStateService;
import queueforge.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobService jobService;
    private final JobStateService jobStateService;
    private final DeadLetterService deadLetterService;
    private final RateLimitService rateLimitService;

    public JobController(JobService jobService, JobStateService jobStateService,
                         DeadLetterService deadLetterService, RateLimitService rateLimitService) {
        this.jobService = jobService;
        this.jobStateService = jobStateService;
        this.deadLetterService = deadLetterService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        rateLimitService.assertAllowed(apiKey);
        JobCreateResult result = jobService.createJob(request, idempotencyKey);
        if (result.replayed()) return ResponseEntity.ok(result.job());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.job().getId()).toUri();
        return ResponseEntity.created(location).body(result.job());
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(jobService.getAllJobs(pageable(page, size)));
    }

    @GetMapping("/dead")
    public ResponseEntity<Page<JobResponse>> getDeadJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(jobService.getDeadJobs(pageable(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable Long id,
            @RequestHeader("X-API-Key") String apiKey) {
        rateLimitService.assertAllowed(apiKey);
        return ResponseEntity.ok(Map.<String, Object>of("jobId", id, "status", jobStateService.cancelJob(id)));
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDeadJob(@PathVariable Long id,
            @RequestHeader("X-API-Key") String apiKey) {
        rateLimitService.assertAllowed(apiKey);
        deadLetterService.replay(id);
        return ResponseEntity.accepted().body(Map.<String, Object>of("jobId", id, "status", "QUEUED"));
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1) throw new IllegalArgumentException("size must be at least 1");
        return PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
