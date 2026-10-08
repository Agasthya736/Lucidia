package com.lucidia.backend.scan;

import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily job that enforces per-user data retention settings.
 * If a user has set a retention period, scans (and their stored images)
 * older than that period are deleted.
 *
 * <p>Users with {@code retentionDays == null} keep data until explicit
 * account deletion — this scheduler does not touch those accounts.
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final UserRepository userRepository;
    private final ScanRepository scanRepository;
    private final ImageStorageService imageStorageService;

    public RetentionScheduler(
            UserRepository userRepository,
            ScanRepository scanRepository,
            ImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.scanRepository = scanRepository;
        this.imageStorageService = imageStorageService;
    }

    /** Runs once per day at 02:00 UTC. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void enforceRetention() {
        log.info("RetentionScheduler: starting daily retention run");
        int deleted = 0;

        for (User user : userRepository.findAll()) {
            if (user.getRetentionDays() == null) continue;

            Instant cutoff = Instant.now().minus(user.getRetentionDays(), ChronoUnit.DAYS);
            List<Scan> expired = scanRepository.findByUserIdAndCreatedAtBefore(user.getId(), cutoff);

            for (Scan scan : expired) {
                try {
                    imageStorageService.deleteAllSlicesForScan(scan.getId(), scan.getSliceCount());
                } catch (Exception e) {
                    log.warn("RetentionScheduler: could not delete images for scanId={}: {}",
                            scan.getId(), e.getMessage());
                }
                scanRepository.delete(scan);
                deleted++;
            }
        }

        log.info("RetentionScheduler: done — deleted {} expired scans", deleted);
    }
}
