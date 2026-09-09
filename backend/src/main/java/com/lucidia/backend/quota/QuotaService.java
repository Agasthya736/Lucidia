package com.lucidia.backend.quota;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final int monthlyLimit;
    private final int rateLimitPerMinute;

    // Track monthly usage: key = "userId:YYYY-MM"
    private final Map<String, AtomicInteger> monthlyUsage = new ConcurrentHashMap<>();

    // Track rate limit tokens: key = userId -> TokenBucket
    private final Map<UUID, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    public QuotaService(
            @Value("${lucidia.quota.monthly-limit:20}") int monthlyLimit,
            @Value("${lucidia.quota.rate-per-minute:5}") int rateLimitPerMinute) {
        this.monthlyLimit = monthlyLimit;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public void checkAndConsume(UUID userId, boolean isByok) {
        if (isByok) {
            log.info("BYOK mode active for user {} - bypassing shared free tier quota.", userId);
            return;
        }

        // 1. Rate limit check (Token bucket per minute)
        TokenBucket bucket = userBuckets.computeIfAbsent(userId, id -> new TokenBucket(rateLimitPerMinute));
        if (!bucket.tryConsume()) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded. Maximum " + rateLimitPerMinute + " scans per minute allowed on free tier.",
                    bucket.getSecondsUntilNextToken()
            );
        }

        // 2. Monthly quota check
        String currentMonthKey = getMonthKey(userId);
        AtomicInteger counter = monthlyUsage.computeIfAbsent(currentMonthKey, k -> new AtomicInteger(0));
        int current = counter.get();

        if (current >= monthlyLimit) {
            throw new QuotaExceededException(
                    "Monthly scan quota exceeded (" + current + "/" + monthlyLimit + "). "
                            + "Please configure your own Gemini API key in Settings (BYOK mode) for unlimited scans.",
                    monthlyLimit,
                    current
            );
        }

        counter.incrementAndGet();
        log.info("Quota consumed for user {}: {}/{} used this month.", userId, current + 1, monthlyLimit);
    }

    public QuotaStatusDto getQuotaStatus(UUID userId, boolean isByok) {
        String currentMonthKey = getMonthKey(userId);
        int used = monthlyUsage.getOrDefault(currentMonthKey, new AtomicInteger(0)).get();
        int remaining = Math.max(0, monthlyLimit - used);

        YearMonth nextMonth = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
        Instant resetsAt = nextMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return new QuotaStatusDto(
                monthlyLimit,
                used,
                remaining,
                resetsAt,
                rateLimitPerMinute,
                isByok
        );
    }

    private String getMonthKey(UUID userId) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        return userId.toString() + ":" + ym.toString();
    }

    private static class TokenBucket {
        private final int capacity;
        private double tokens;
        private long lastRefillTimestamp;

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized long getSecondsUntilNextToken() {
            refill();
            if (tokens >= 1.0) return 0;
            double needed = 1.0 - tokens;
            double fillRatePerMs = (double) capacity / 60000.0;
            return Math.max(1, (long) Math.ceil((needed / fillRatePerMs) / 1000.0));
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed > 0) {
                double tokensToAdd = elapsed * ((double) capacity / 60000.0);
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
