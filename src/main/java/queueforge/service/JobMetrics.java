package queueforge.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import queueforge.entity.JobStatus;
import queueforge.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {
    private final Counter created;
    private final Counter completed;
    private final Counter failed;
    private final Counter recovered;

    public JobMetrics(MeterRegistry registry, JobRepository repository) {
        this.created = Counter.builder("queueforge.jobs.created").register(registry);
        this.completed = Counter.builder("queueforge.jobs.completed").register(registry);
        this.failed = Counter.builder("queueforge.jobs.failed").register(registry);
        this.recovered = Counter.builder("queueforge.jobs.recovered").register(registry);
        Gauge.builder("queueforge.jobs.queued", repository, r -> r.countByStatus(JobStatus.QUEUED)).register(registry);
        Gauge.builder("queueforge.jobs.processing", repository, r -> r.countByStatus(JobStatus.PROCESSING)).register(registry);
        Gauge.builder("queueforge.jobs.dead", repository, r -> r.countByStatus(JobStatus.DEAD)).register(registry);
    }
    public void jobCreated() { created.increment(); }
    public void jobCompleted() { completed.increment(); }
    public void jobFailed() { failed.increment(); }
    public void jobsRecovered(long count) { recovered.increment(count); }
}
