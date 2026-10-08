package com.lucidia.backend.consent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Records and checks user consent for the current consent version.
 * POST /api/scans is blocked until consent has been accepted.
 */
@Service
public class ConsentService {

    /** Bump this constant when the consent text materially changes. */
    public static final String CURRENT_VERSION = "1.0";

    private final UserConsentRepository repository;

    public ConsentService(UserConsentRepository repository) {
        this.repository = repository;
    }

    /** Returns true when the user has accepted the current consent version. */
    public boolean hasConsented(UUID userId) {
        return repository.findByUserIdAndVersion(userId, CURRENT_VERSION)
                .map(UserConsent::isAccepted)
                .orElse(false);
    }

    /**
     * Records or updates a consent decision.
     * If the user previously declined and now accepts (or vice-versa), the old row
     * is replaced.
     */
    @Transactional
    public UserConsent recordConsent(UUID userId, boolean accepted) {
        Optional<UserConsent> existing =
                repository.findByUserIdAndVersion(userId, CURRENT_VERSION);

        if (existing.isPresent()) {
            // Delete old row and insert fresh to update the timestamp
            repository.delete(existing.get());
            repository.flush();
        }

        return repository.save(new UserConsent(userId, accepted, CURRENT_VERSION));
    }

    /** Called during account deletion. */
    @Transactional
    public void deleteForUser(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
