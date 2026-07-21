package com.lucidia.backend.api;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import com.lucidia.backend.dto.ScanDtos.*;
import com.lucidia.backend.dto.ScanDtos.ScanDetail;
import com.lucidia.backend.dto.ScanDtos.ScanSummary;
import com.lucidia.backend.scan.Scan;
import com.lucidia.backend.scan.ScanService;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScanController(ScanService scanService, UserRepository userRepository) {
        this.scanService = scanService;
        this.userRepository = userRepository;
    }

    private UUID currentUserId(Jwt jwt) {
        User user = userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        return user.getId();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ScanSummary> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("image") MultipartFile image) throws IOException {
        UUID userId = currentUserId(jwt);
        Scan scan = scanService.submit(userId, image.getOriginalFilename(), image.getBytes(),
                image.getContentType() != null ? image.getContentType() : "image/jpeg");
        return ResponseEntity.status(202).body(ScanSummary.from(scan));
    }

    @GetMapping
    public List<ScanSummary> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserId(jwt);
        return scanService.listForUser(userId).stream().map(ScanSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ScanDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) throws IOException {
        UUID userId = currentUserId(jwt);
        Scan scan = scanService.get(id, userId);
        return toDetail(scan);
    }

    @PatchMapping("/{id}/finalize")
    public ScanDetail finalizeScan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) throws IOException {
        UUID userId = currentUserId(jwt);
        Scan scan = scanService.finalizeScan(id, userId);
        return toDetail(scan);
    }

    private ScanDetail toDetail(Scan scan) throws IOException {
        return new ScanDetail(
                scan.getId(), scan.getStatus().name(), scan.getImageFilename(),
                parseOrNull(scan.getVisionAJson()),
                parseOrNull(scan.getVisionBJson()),
                parseOrNull(scan.getArbitrationJson()),
                parseOrNull(scan.getReportJson()),
                parseOrNull(scan.getVerificationJson()),
                scan.getErrorMessage(),
                scan.getCreatedAt(), scan.getCompletedAt(), scan.getFinalizedAt()
        );
    }

    private Object parseOrNull(String json) throws IOException {
        return json == null ? null : objectMapper.readValue(json, Object.class);
    }
}