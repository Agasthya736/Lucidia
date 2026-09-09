package com.lucidia.backend.quota;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaServiceTest {

    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        // limit = 3 scans per month, rate = 5 per minute
        quotaService = new QuotaService(3, 5);
    }

    @Test
    void testQuotaConsumptionWithinLimit() {
        UUID userId = UUID.randomUUID();

        quotaService.checkAndConsume(userId, false);
        quotaService.checkAndConsume(userId, false);

        QuotaStatusDto status = quotaService.getQuotaStatus(userId, false);
        assertEquals(3, status.monthlyLimit());
        assertEquals(2, status.usedThisMonth());
        assertEquals(1, status.remainingThisMonth());
        assertFalse(status.byokActive());
    }

    @Test
    void testQuotaExceededThrowsException() {
        UUID userId = UUID.randomUUID();

        quotaService.checkAndConsume(userId, false);
        quotaService.checkAndConsume(userId, false);
        quotaService.checkAndConsume(userId, false);

        assertThrows(QuotaExceededException.class, () -> quotaService.checkAndConsume(userId, false));
    }

    @Test
    void testByokBypassesQuota() {
        UUID userId = UUID.randomUUID();

        // Should consume indefinitely when BYOK is active without throwing
        for (int i = 0; i < 10; i++) {
            quotaService.checkAndConsume(userId, true);
        }

        QuotaStatusDto status = quotaService.getQuotaStatus(userId, true);
        assertTrue(status.byokActive());
    }
}
