package queueforge.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueueForgeConfigurationValidator implements InitializingBean {
    private final int concurrency;
    private final int leaseSeconds;
    private final long heartbeatMillis;
    private final long pollMillis;
    private final long recoveryMillis;
    private final String activeProfiles;
    private final String databasePassword;
    private final String redisPassword;
    private final String apiKey;

    public QueueForgeConfigurationValidator(
            @Value("${queueforge.worker.concurrency:4}") int concurrency,
            @Value("${queueforge.worker.lease-duration-seconds:30}") int leaseSeconds,
            @Value("${queueforge.worker.heartbeat-interval-ms:10000}") long heartbeatMillis,
            @Value("${queueforge.worker.poll-interval-ms:500}") long pollMillis,
            @Value("${queueforge.worker.recovery-interval-ms:5000}") long recoveryMillis,
            @Value("${spring.profiles.active:}") String activeProfiles,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${queueforge.security.api-key}") String apiKey) {
        this.concurrency = concurrency;
        this.leaseSeconds = leaseSeconds;
        this.heartbeatMillis = heartbeatMillis;
        this.pollMillis = pollMillis;
        this.recoveryMillis = recoveryMillis;
        this.activeProfiles = activeProfiles;
        this.databasePassword = databasePassword;
        this.redisPassword = redisPassword;
        this.apiKey = apiKey;
    }

    @Override
    public void afterPropertiesSet() {
        if (concurrency < 1 || concurrency > 64) {
            throw new IllegalStateException("queueforge.worker.concurrency must be between 1 and 64");
        }
        if (leaseSeconds < 10) {
            throw new IllegalStateException("Worker lease duration must be at least 10 seconds");
        }
        if (heartbeatMillis < 1000 || heartbeatMillis >= leaseSeconds * 500L) {
            throw new IllegalStateException("Heartbeat interval must be at least 1 second and less than half the lease duration");
        }
        if (pollMillis < 100 || recoveryMillis < 1000) {
            throw new IllegalStateException("Worker polling must be >=100ms and recovery interval >=1000ms");
        }
        if (activeProfiles.contains("prod")) {
            requireProductionSecret("DB_PASSWORD", databasePassword, 24);
            requireProductionSecret("REDIS_PASSWORD", redisPassword, 24);
            requireProductionSecret("QUEUEFORGE_API_KEY", apiKey, 32);
        }
    }

    private void requireProductionSecret(String name, String value, int minimumLength) {
        if (value == null || value.length() < minimumLength
                || value.startsWith("REPLACE_") || value.startsWith("CHANGE_ME")
                || value.equalsIgnoreCase("password")) {
            throw new IllegalStateException(name + " must be replaced with a strong secret before production startup");
        }
    }
}
