package com.lucidia.backend.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ScanFeedbackRepository extends JpaRepository<ScanFeedback, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ScanFeedback f WHERE f.userId = :userId")
    void deleteByUserId(UUID userId);
}
