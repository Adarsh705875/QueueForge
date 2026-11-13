package queueforge.worker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "queueforge.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerDispatcher {
    private final JobWorker jobWorker;
    private final ThreadPoolTaskExecutor executor;
    private final int concurrency;
    private final AtomicInteger activeWorkers = new AtomicInteger();

    public WorkerDispatcher(JobWorker jobWorker,
            @Qualifier("jobWorkerExecutor") ThreadPoolTaskExecutor executor,
            @Value("${queueforge.worker.concurrency:4}") int concurrency) {
        this.jobWorker = jobWorker;
        this.executor = executor;
        this.concurrency = concurrency;
    }

    @Scheduled(fixedDelayString = "${queueforge.worker.poll-interval-ms:500}")
    public void dispatch() {
        for (int i = 0; i < concurrency; i++) {
            if (!reserveWorkerSlot()) break;
            try {
                executor.execute(() -> {
                    try { jobWorker.processNextJob(); }
                    finally { activeWorkers.decrementAndGet(); }
                });
            } catch (TaskRejectedException ex) {
                activeWorkers.decrementAndGet();
                break;
            }
        }
    }

    private boolean reserveWorkerSlot() {
        while (true) {
            int current = activeWorkers.get();
            if (current >= concurrency) return false;
            if (activeWorkers.compareAndSet(current, current + 1)) return true;
        }
    }
}
