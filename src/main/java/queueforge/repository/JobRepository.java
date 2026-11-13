package queueforge.repository;

import queueforge.entity.Job;
import queueforge.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = """
            SELECT id FROM jobs
            WHERE (status = 'QUEUED'
                   AND (scheduled_at IS NULL OR scheduled_at <= CURRENT_TIMESTAMP))
               OR (status = 'FAILED'
                   AND next_retry_at IS NOT NULL
                   AND next_retry_at <= CURRENT_TIMESTAMP)
            ORDER BY priority DESC, created_at ASC, id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Long> findNextJobIdForUpdate();

    Optional<Job> findByIdempotencyKey(String idempotencyKey);
    Page<Job> findAllByStatus(JobStatus status, Pageable pageable);
    long countByStatus(JobStatus status);

    @Modifying
    @Query(value = """
            UPDATE jobs
            SET heartbeat_at = clock_timestamp(),
                lease_expires_at = clock_timestamp() + make_interval(secs => :leaseSeconds),
                row_version = row_version + 1
            WHERE id = :jobId AND status = 'PROCESSING'
              AND lease_owner = :workerId AND lease_version = :leaseVersion
              AND lease_expires_at > clock_timestamp()
            """, nativeQuery = true)
    int extendLease(@Param("jobId") Long jobId,
                    @Param("workerId") String workerId,
                    @Param("leaseVersion") long leaseVersion,
                    @Param("leaseSeconds") int leaseSeconds);

    @Modifying
    @Query(value = """
            UPDATE jobs
            SET status = 'COMPLETED', last_error = NULL, next_retry_at = NULL,
                lease_owner = NULL, lease_expires_at = NULL, heartbeat_at = NULL,
                row_version = row_version + 1
            WHERE id = :jobId AND status = 'PROCESSING'
              AND lease_owner = :workerId AND lease_version = :leaseVersion
              AND lease_expires_at > clock_timestamp()
            """, nativeQuery = true)
    int completeIfOwned(@Param("jobId") Long jobId,
                        @Param("workerId") String workerId,
                        @Param("leaseVersion") long leaseVersion);

    @Modifying
    @Query(value = """
            UPDATE jobs
            SET status = CASE WHEN attempts >= max_attempts THEN 'DEAD' ELSE 'FAILED' END,
                last_error = :error,
                next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL
                    ELSE clock_timestamp() + make_interval(secs => :retryDelaySeconds) END,
                lease_owner = NULL, lease_expires_at = NULL, heartbeat_at = NULL,
                row_version = row_version + 1
            WHERE id = :jobId AND status = 'PROCESSING'
              AND lease_owner = :workerId AND lease_version = :leaseVersion
              AND lease_expires_at > clock_timestamp()
            """, nativeQuery = true)
    int failIfOwned(@Param("jobId") Long jobId,
                    @Param("workerId") String workerId,
                    @Param("leaseVersion") long leaseVersion,
                    @Param("error") String error,
                    @Param("retryDelaySeconds") int retryDelaySeconds);

    @Modifying
    @Query(value = """
            UPDATE jobs
            SET status = CASE WHEN attempts >= max_attempts THEN 'DEAD' ELSE 'FAILED' END,
                last_error = 'Worker lease expired; job recovered',
                next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL
                    ELSE clock_timestamp() + make_interval(secs => LEAST(300, power(2, LEAST(attempts, 8))::integer)) END,
                lease_owner = NULL, lease_expires_at = NULL, heartbeat_at = NULL,
                lease_version = lease_version + 1,
                row_version = row_version + 1
            WHERE status = 'PROCESSING'
              AND (lease_expires_at IS NULL OR lease_expires_at <= clock_timestamp())
            """, nativeQuery = true)
    int recoverExpiredLeases();
}
