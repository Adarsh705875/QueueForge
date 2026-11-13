package queueforge.worker;

import queueforge.entity.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DemoJobHandler implements JobHandler {

    private static final Logger log =
            LoggerFactory.getLogger(DemoJobHandler.class);

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "SEND_EMAIL",
            "GENERATE_REPORT",
            "BACKUP_DATABASE",
            "TEST_JOB"
    );

    @Override
    public boolean supports(String jobType) {
        return SUPPORTED_TYPES.contains(jobType);
    }

    @Override
    public void execute(Job job) throws Exception {

        log.info(
                "Executing job ID={}, type={}",
                job.getId(),
                job.getJobType()
        );

        String payload = job.getPayload();

        if (payload != null && payload.startsWith("FAIL:")) {
            throw new IllegalStateException(
                    "Simulated failure requested by test payload"
            );
        }

        if (payload != null && payload.startsWith("SLEEP:")) {

            long milliseconds = Long.parseLong(
                    payload.substring(6)
            );

            log.info(
                    "Job {} sleeping for {} milliseconds",
                    job.getId(),
                    milliseconds
            );

            Thread.sleep(milliseconds);
        }

        log.info("Job {} executed successfully", job.getId());
    }
}