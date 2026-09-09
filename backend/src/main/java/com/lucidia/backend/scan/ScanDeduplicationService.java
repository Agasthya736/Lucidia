package com.lucidia.backend.scan;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lucidia.backend.triage.SliceInput;

@Service
public class ScanDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScanDeduplicationService.class);

    private final ScanRepository scanRepository;

    public ScanDeduplicationService(ScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    public String computeStudyHash(List<SliceInput> slices) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (SliceInput slice : slices) {
                md.update(slice.bytes());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public Optional<Scan> findExistingStudy(UUID userId, String studyHash) {
        if (studyHash == null || studyHash.isBlank()) {
            return Optional.empty();
        }

        List<Scan> matches = scanRepository.findByUserIdAndStudyHashAndStatus(
                userId, studyHash, Scan.Status.COMPLETED
        );

        if (!matches.isEmpty()) {
            Scan cached = matches.get(0);
            log.info("Deduplication cache hit for user {} on study hash {}: reusing scan {}",
                    userId, studyHash, cached.getId());
            return Optional.of(cached);
        }

        return Optional.empty();
    }
}
