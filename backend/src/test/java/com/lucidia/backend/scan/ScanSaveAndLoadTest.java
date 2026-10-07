package com.lucidia.backend.scan;

import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.api.ScanController;
import com.lucidia.backend.audit.AuditLogService;
import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import com.lucidia.backend.dto.ScanDtos.ScanDetail;
import com.lucidia.backend.dto.ScanDtos.ScanSummary;
import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.orchestrator.PipelineResult;
import com.lucidia.backend.quota.QuotaService;
import com.lucidia.backend.quota.QuotaStatusDto;
import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.synthesis.RegionalFinding;
import com.lucidia.backend.synthesis.ReportSynthesisService;
import com.lucidia.backend.triage.AggregatedFindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanSaveAndLoadTest {

    private final Map<UUID, Scan> dbStore = new ConcurrentHashMap<>();
    private ScanRepository scanRepository;
    private UserRepository userRepository;
    private AuditLogService auditLogService;
    private ImageStorageService imageStorageService;
    private QuotaService quotaService;
    private ScanDeduplicationService deduplicationService;
    private PipelineOrchestrator pipelineOrchestrator;
    private ReportSynthesisService reportSynthesisService;
    private ReportPdfService reportPdfService;
    private com.lucidia.backend.responsibleai.ResponsibleAiGuardrailService responsibleAiService;

    private AsyncPipelineExecutor asyncPipelineExecutor;
    private ScanService scanService;
    private ScanController scanController;

    private User testUser;
    private Jwt testJwt;

    @BeforeEach
    void setUp() throws Exception {
        dbStore.clear();

        // Simulated persistent repository
        scanRepository = mock(ScanRepository.class);
        when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> {
            Scan scan = invocation.getArgument(0);
            if (scan.getId() == null) {
                Field idField = Scan.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(scan, UUID.randomUUID());
            }
            dbStore.put(scan.getId(), scan);
            return scan;
        });

        when(scanRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(dbStore.get(id));
        });

        when(scanRepository.findByUserIdOrderByCreatedAtDesc(any(UUID.class))).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            List<Scan> list = new ArrayList<>();
            for (Scan s : dbStore.values()) {
                if (s.getUserId().equals(userId)) {
                    list.add(s);
                }
            }
            list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            return list;
        });

        when(scanRepository.findByUserIdAndStudyHashAndStatus(any(UUID.class), anyString(), any(Scan.Status.class)))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    String hash = invocation.getArgument(1);
                    Scan.Status status = invocation.getArgument(2);
                    List<Scan> list = new ArrayList<>();
                    for (Scan s : dbStore.values()) {
                        if (s.getUserId().equals(userId) && hash.equals(s.getStudyHash()) && s.getStatus() == status) {
                            list.add(s);
                        }
                    }
                    return list;
                });

        UUID userId = UUID.randomUUID();
        testUser = new User("Test Patient", "patient@example.com", "hash", "LOCAL", null);
        Field userIdField = User.class.getDeclaredField("id");
        userIdField.setAccessible(true);
        userIdField.set(testUser, userId);

        userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail("patient@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        auditLogService = mock(AuditLogService.class);
        imageStorageService = mock(ImageStorageService.class);
        quotaService = mock(QuotaService.class);
        when(quotaService.getQuotaStatus(any(), any(Boolean.class)))
                .thenReturn(new QuotaStatusDto(3, 1, 2, Instant.now().plusSeconds(86400), 10, false));

        deduplicationService = new ScanDeduplicationService(scanRepository);

        // Orchestrator returning a valid grounded pipeline result
        pipelineOrchestrator = mock(PipelineOrchestrator.class);
        AggregatedFindings sampleFindings = new AggregatedFindings(
                1, 0, "NORMAL", 0.95, List.of(), List.of(), Map.of(), "Clean scan"
        );
        GroundedReport sampleReport = new GroundedReport(
                List.of(new RegionalFinding("Lungs", "NORMAL", "Clear", List.of())),
                "No abnormality detected",
                "ROUTINE",
                0.95,
                "Routine check",
                "Lucidia Test",
                true
        );
        VerificationResult sampleVerification = VerificationResult.of(true, List.of(), "Verified", 1.0);
        PipelineResult sampleResult = new PipelineResult(sampleFindings, sampleReport, sampleVerification, false, List.of());

        when(pipelineOrchestrator.run(any(), any(), any(), any())).thenReturn(sampleResult);

        reportSynthesisService = mock(ReportSynthesisService.class);
        reportPdfService = mock(ReportPdfService.class);

        responsibleAiService = mock(com.lucidia.backend.responsibleai.ResponsibleAiGuardrailService.class);

        asyncPipelineExecutor = new AsyncPipelineExecutor(scanRepository, pipelineOrchestrator);
        scanService = new ScanService(
                scanRepository,
                asyncPipelineExecutor,
                auditLogService,
                imageStorageService,
                quotaService,
                deduplicationService,
                responsibleAiService
        );

        scanController = new ScanController(
                scanService,
                userRepository,
                reportPdfService,
                imageStorageService,
                quotaService,
                reportSynthesisService
        );

        testJwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "patient@example.com", "userId", userId.toString())
        );
    }

    @Test
    @DisplayName("Submit scan end to end, wait for COMPLETED, then fetch with GET /api/scans and GET /api/scans/{id}")
    void testSubmitScanWaitCompletedFetchListAndDetail() throws Exception {
        // 1. Submit scan with mock image file
        byte[] fakeImageBytes = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0 };
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "chest_ct_slice.jpg",
                "image/jpeg",
                fakeImageBytes
        );

        ResponseEntity<ScanSummary> submitResponse = scanController.submit(
                testJwt,
                List.of(file),
                null,
                "CT_SERIES",
                "Patient presenting for informational check"
        );

        assertEquals(202, submitResponse.getStatusCode().value(), "POST /api/scans should return 202 ACCEPTED");
        ScanSummary summary = submitResponse.getBody();
        assertNotNull(summary);
        UUID scanId = summary.id();
        assertNotNull(scanId);

        // 2. Wait for COMPLETED (poll status from dbStore)
        long deadline = System.currentTimeMillis() + 5000;
        Scan storedScan = null;
        while (System.currentTimeMillis() < deadline) {
            storedScan = dbStore.get(scanId);
            if (storedScan != null && storedScan.getStatus() == Scan.Status.COMPLETED) {
                break;
            }
            Thread.sleep(50);
        }

        assertNotNull(storedScan, "Scan must be present in database");
        assertEquals(Scan.Status.COMPLETED, storedScan.getStatus(), "Scan must reach COMPLETED status");
        assertNotNull(storedScan.getReportJson(), "Report JSON must be persisted");
        assertNotNull(storedScan.getTriageJson(), "Triage JSON must be persisted");

        // 3. Fetch with GET /api/scans (list)
        List<ScanSummary> scanList = scanController.list(testJwt);
        assertNotNull(scanList);
        assertFalse(scanList.isEmpty(), "Scan list must not be empty");
        boolean foundInList = scanList.stream().anyMatch(s -> s.id().equals(scanId));
        assertTrue(foundInList, "Completed scan must appear in GET /api/scans list");

        // 4. Fetch with GET /api/scans/{id} (detail)
        ScanDetail detail = scanController.get(testJwt, scanId);
        assertNotNull(detail, "GET /api/scans/{id} must return detail");
        assertEquals(scanId, detail.id());
        assertEquals("COMPLETED", detail.status());
        assertEquals("chest_ct_slice.jpg", detail.imageFilename());
        assertEquals(1, detail.sliceCount());
        assertNotNull(detail.report(), "Report in detail must be populated from database JSON");
        assertNotNull(detail.triage(), "Triage in detail must be populated from database JSON");
        assertNotNull(detail.completedAt(), "completedAt timestamp must be set");

        // 5. Simulate App Restart: instantiate brand new service and controller pointing to same database
        ScanService newScanService = new ScanService(
                scanRepository,
                asyncPipelineExecutor,
                auditLogService,
                imageStorageService,
                quotaService,
                deduplicationService,
                responsibleAiService
        );
        ScanController newScanController = new ScanController(
                newScanService,
                userRepository,
                reportPdfService,
                imageStorageService,
                quotaService,
                reportSynthesisService
        );

        List<ScanSummary> reloadedList = newScanController.list(testJwt);
        assertTrue(reloadedList.stream().anyMatch(s -> s.id().equals(scanId)),
                "Scan must still appear in list after simulated service restart");

        ScanDetail reloadedDetail = newScanController.get(testJwt, scanId);
        assertEquals(scanId, reloadedDetail.id());
        assertEquals("COMPLETED", reloadedDetail.status());
        assertNotNull(reloadedDetail.report());
    }
}
