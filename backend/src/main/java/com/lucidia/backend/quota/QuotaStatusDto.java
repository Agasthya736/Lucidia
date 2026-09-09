package com.lucidia.backend.quota;

import java.time.Instant;

public record QuotaStatusDto(
        int monthlyLimit,
        int usedThisMonth,
        int remainingThisMonth,
        Instant resetsAt,
        int rateLimitPerMinute,
        boolean byokActive
) {}
