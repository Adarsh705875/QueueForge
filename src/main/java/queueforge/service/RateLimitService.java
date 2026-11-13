package queueforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import queueforge.exception.RateLimitExceededException;
import queueforge.exception.RateLimiterUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class RateLimitService {
    private static final DefaultRedisScript<Long> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local refill_per_second = tonumber(ARGV[2])
            local redis_time = redis.call('TIME')
            local now_ms = tonumber(redis_time[1]) * 1000 + math.floor(tonumber(redis_time[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated_at')
            local tokens = tonumber(state[1]) or capacity
            local updated_at = tonumber(state[2]) or now_ms
            local elapsed = math.max(0, now_ms - updated_at) / 1000
            tokens = math.min(capacity, tokens + elapsed * refill_per_second)
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated_at', now_ms)
            redis.call('EXPIRE', KEYS[1], math.ceil((capacity / refill_per_second) * 2))
            return allowed
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int capacity;
    private final double refillPerSecond;

    public RateLimitService(StringRedisTemplate redis,
            @Value("${queueforge.rate-limit.capacity:100}") int capacity,
            @Value("${queueforge.rate-limit.refill-per-second:10}") double refillPerSecond) {
        if (capacity < 1 || refillPerSecond <= 0) throw new IllegalArgumentException("Rate-limit capacity and refill rate must be positive");
        this.redis = redis;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public void assertAllowed(String apiKey) {
        try {
            String redisKey = "queueforge:rate-limit:" + sha256(apiKey);
            Long allowed = redis.execute(TOKEN_BUCKET, List.of(redisKey),
                    Integer.toString(capacity), Double.toString(refillPerSecond));
            if (allowed == null) throw new RateLimiterUnavailableException("Rate limiter returned no result");
            if (allowed != 1L) throw new RateLimitExceededException("Job submission rate limit exceeded");
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new RateLimiterUnavailableException("Rate limiter is temporarily unavailable", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
