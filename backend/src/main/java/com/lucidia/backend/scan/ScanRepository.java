package com.lucidia.backend.scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScanRepository extends JpaRepository<Scan, UUID> {
    List<Scan> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Scan> findByUserIdAndStudyHashAndStatus(UUID userId, String studyHash, Scan.Status status);
    List<Scan> findByUserId(UUID userId);

    /** Returns scans created before the cutoff for a user (for retention enforcement). */
    @Query("SELECT s FROM Scan s WHERE s.userId = :userId AND s.createdAt < :cutoff")
    List<Scan> findByUserIdAndCreatedAtBefore(UUID userId, Instant cutoff);
}