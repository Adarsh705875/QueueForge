package queueforge.service;

import queueforge.dto.JobRequest;
import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import queueforge.exception.IdempotencyConflictException;
import queueforge.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    @Mock JobRepository repository;
    @Mock JobCreationService creationService;
    @Mock JobMetrics metrics;
    private JobService service;

    @BeforeEach
    void setUp() { service = new JobService(repository, creationService, metrics, false); }

    @Test
    void sameIdempotencyKeyAndSameRequestReturnsExistingJob() {
        JobRequest request = request("hello");
        String hash = IdempotencyHasher.hash("SEND_EMAIL", "hello", 5, 5, null);
        Job existing = existingJob(hash);
        when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        var result = service.createJob(request, "same-key");

        assertTrue(result.replayed());
        assertEquals("SEND_EMAIL", result.job().getJobType());
        assertEquals("hello", result.job().getPayload());
        verifyNoInteractions(creationService);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturnsConflict() {
        Job existing = existingJob(IdempotencyHasher.hash("SEND_EMAIL", "old", 5, 5, null));
        when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class,
                () -> service.createJob(request("new"), "same-key"));
        verifyNoInteractions(creationService);
    }

    private JobRequest request(String payload) {
        JobRequest request = new JobRequest();
        request.setJobType("SEND_EMAIL");
        request.setPayload(payload);
        request.setPriority(5);
        request.setMaxAttempts(5);
        return request;
    }

    private Job existingJob(String hash) {
        Job job = new Job();
        job.setJobType("SEND_EMAIL");
        job.setPayload("hello");
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(5);
        job.setAttempts(0);
        job.setMaxAttempts(5);
        job.setIdempotencyKey("same-key");
        job.setRequestHash(hash);
        return job;
    }
}
