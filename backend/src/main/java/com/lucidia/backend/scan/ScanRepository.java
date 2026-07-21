package com.lucidia.backend.scan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<Scan, UUID> {
    List<Scan> findByUserIdOrderByCreatedAtDesc(UUID userId);
}