package com.lucidia.backend.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import com.lucidia.backend.dto.ScanDtos.FinalizeRequest;
import com.lucidia.backend.dto.ScanDtos.ScanDetail;
import com.lucidia.backend.dto.ScanDtos.ScanSummary;
import com.lucidia.backend.quota.QuotaExceededException;
import com.lucidia.backend.quota.QuotaService;
import com.lucidia.backend.quota.QuotaStatusDto;
import com.lucidia.backend.quota.RateLimitExceededException;
import com.lucidia.backend.scan.ImageStorageService;
import com.lucidia.backend.scan.ReportPdfService;
import com.lucidia.backend.scan.Scan;
import com.lucidia.backend.scan.ScanService;
import com.lucidia.backend.triage.SliceInput;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final UserRepository userRepository;
    private final ReportPdfService reportPdfService;
    private final ImageStorageService imageStorageService;
    private final QuotaService quotaService;
    private final com.lucidia.backend.synthesis.ReportSynthesisService reportSynthesisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScanController(
            ScanService scanService,
            UserRepository userRepository,
            ReportPdfService reportPdfService,
            ImageStorageService imageStorageService,
            QuotaService quotaService,
            com.lucidia.backend.synthesis.ReportSynthesisService reportSynthesisService) {
        this.scanService = scanService;
        this.userRepository = userRepository;
        this.reportPdfService = reportPdfService;
        this.imageStorageService = imageStorageService;
        this.quotaService = quotaService;
        this.reportSynthesisService = reportSynthesisService;
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        User user = userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        return user.getId();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanSummary> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "image", required = false) MultipartFile singleImage,
            @RequestParam(value = "modality", required = false, defaultValue = "CT_SERIES") String modality,
            @RequestParam(value = "clinicalNotes", required = false) String clinicalNotes,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String customApiKey)
            throws IOException {

        UUID userId = currentUserId(jwt);

        List<MultipartFile> inputFiles = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            inputFiles.addAll(images);
        } else if (singleImage != null) {
            inputFiles.add(singleImage);
        } else {
            return ResponseEntity.badRequest().build();
        }

        List<SliceInput> slices = new ArrayList<>();
        for (int i = 0; i < inputFiles.size(); i++) {
            MultipartFile file = inputFiles.get(i);
            String mime = file.getContentType() != null ? file.getContentType() : MediaType.IMAGE_JPEG_VALUE;
            slices.add(new SliceInput(i, file.getOriginalFilename(), file.getBytes(), mime));
        }

        Scan scan = scanService.submit(userId, slices, modality, clinicalNotes, customApiKey);

        return ResponseEntity
                .accepted()
                .body(ScanSummary.from(scan));
    }

    @GetMapping
    public List<ScanSummary> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserId(jwt);
        return scanService.listForUser(userId)
                .stream()
                .map(ScanSummary::from)
                .toList();
    }

    @GetMapping("/quota")
    public QuotaStatusDto getQuota(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String customApiKey) {
        UUID userId = currentUserId(jwt);
        boolean isByok = customApiKey != null && !customApiKey.isBlank();
        return quotaService.getQuotaStatus(userId, isByok);
    }

    @GetMapping("/{id}")
    public ScanDetail get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id)
            throws IOException {

        UUID userId = currentUserId(jwt);
        Scan scan = scanService.get(id, userId);
        return toDetail(scan);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return getSliceImage(jwt, id, 0);
    }

    @GetMapping("/{id}/slices/{sliceIndex}")
    public ResponseEntity<byte[]> getSliceImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable int sliceIndex) {

        UUID userId = currentUserId(jwt);
        Scan scan = scanService.get(id, userId);

        byte[] imageBytes = imageStorageService.loadSlice(id, sliceIndex);
        MediaType contentType = inferContentType(scan.getImageFilename());

        return ResponseEntity.ok()
                .contentType(contentType)
                .body(imageBytes);
    }

    @PatchMapping("/{id}/finalize")
    public ResponseEntity<ScanDetail> finalizeScan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) FinalizeRequest req)
            throws IOException {

        UUID userId = currentUserId(jwt);

        String reviewerName = req != null && req.reviewerName() != null && !req.reviewerName().isBlank()
                ? req.reviewerName()
                : "Dr. Reviewing Clinician";
        String reviewerCreds = req != null && req.reviewerCredentials() != null && !req.reviewerCredentials().isBlank()
                ? req.reviewerCredentials()
                : "MD, Board Certified Radiologist";
        String notes = req != null ? req.notes() : "";

        Scan scan = scanService.finalizeScan(id, userId, reviewerName, reviewerCreds, notes);
        return ResponseEntity.ok(toDetail(scan));
    }

    @GetMapping(value = "/{id}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> downloadReportPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id)
            throws IOException {

        UUID userId = currentUserId(jwt);
        Scan scan = scanService.get(id, userId);

        byte[] pdf = reportPdfService.generate(scan);

        String filename = String.format(
                "LUCIDIA_REPORT_%s.pdf",
                scan.getId().toString().substring(0, 8).toUpperCase());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<Map<String, String>> askQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String customApiKey) {

        UUID userId = currentUserId(jwt);
        Scan scan = scanService.get(id, userId);

        String question = request.getOrDefault("question", "");
        if (question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be empty"));
        }

        String reportJson = scan.getReportJson() != null ? scan.getReportJson() : "{}";
        String answer = reportSynthesisService.answerQuestion(reportJson, question, customApiKey);

        return ResponseEntity.ok(Map.of("answer", answer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {

        UUID userId = currentUserId(jwt);
        scanService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<?> handleQuotaExceeded(QuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "code", "QUOTA_EXCEEDED",
                        "message", ex.getMessage(),
                        "monthlyLimit", ex.getMonthlyLimit(),
                        "used", ex.getUsed()
                ));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", ex.getMessage(),
                        "retryAfterSeconds", ex.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler(com.lucidia.backend.responsibleai.ResponsibleAiException.class)
    public ResponseEntity<?> handleResponsibleAiViolation(com.lucidia.backend.responsibleai.ResponsibleAiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", "RESPONSIBLE_AI_POLICY_VIOLATION",
                        "message", ex.getMessage()
                ));
    }

    private ScanDetail toDetail(Scan scan) throws IOException {
        return new ScanDetail(
                scan.getId(),
                scan.getStatus().name(),
                scan.getImageFilename(),
                scan.getSliceCount(),
                parseOrNull(scan.getSliceFilenamesJson()),
                scan.getModality(),
                scan.getClinicalNotes(),
                scan.isEscalated(),
                parseOrNull(scan.getTriageJson()),
                parseOrNull(scan.getReportJson()),
                parseOrNull(scan.getVerificationJson()),
                scan.getReviewerName(),
                scan.getReviewerCredentials(),
                scan.getSignOffNotes(),
                // Backwards compatibility fallbacks
                parseOrNull(scan.getVisionAJson()),
                parseOrNull(scan.getVisionBJson()),
                parseOrNull(scan.getArbitrationJson()),
                parseOrNull(scan.getMedSamJson()),
                scan.getErrorMessage(),
                scan.getCreatedAt(),
                scan.getCompletedAt(),
                scan.getFinalizedAt()
        );
    }

    private Object parseOrNull(String json) throws IOException {
        if (json == null || json.isBlank()) return null;
        return objectMapper.readValue(json, Object.class);
    }

    private MediaType inferContentType(String filename) {
        if (filename == null) return MediaType.IMAGE_JPEG;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.valueOf("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }
}