package com.lucidia.backend.responsibleai;

import com.lucidia.backend.audit.AuditLogService;
import com.lucidia.backend.quota.QuotaService;
import com.lucidia.backend.scan.AsyncPipelineExecutor;
import com.lucidia.backend.scan.ImageStorageService;
import com.lucidia.backend.scan.ScanDeduplicationService;
import com.lucidia.backend.scan.ScanRepository;
import com.lucidia.backend.scan.ScanService;
import com.lucidia.backend.triage.SliceInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResponsibleAiGuardrailServiceTest {

    private GeminiImageVerifier mockGeminiVerifier;
    private ResponsibleAiGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        mockGeminiVerifier = mock(GeminiImageVerifier.class);
        guardrailService = new ResponsibleAiGuardrailService(mockGeminiVerifier);
    }

    private byte[] createSyntheticImage(int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        // Draw some subtle noise/variation to satisfy non-monochrome heuristics
        g.setColor(new Color(210, 160, 130));
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] createCtImage(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        // Thoracic structure
        g.setColor(new Color(180, 180, 180));
        g.fillOval(10, 10, width - 20, height - 20);
        g.setColor(new Color(40, 40, 40));
        g.fillOval(30, 30, width / 3, height / 2);
        g.fillOval(width / 2, 30, width / 3, height / 2);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Non-image file (e.g. text file) is rejected with clear message")
    void testNonImageFileRejected() {
        byte[] textBytes = "This is a plain text file, not a diagnostic medical scan.".getBytes();
        SliceInput slice = new SliceInput(0, "notes.txt", textBytes, "text/plain");

        ResponsibleAiException ex = assertThrows(ResponsibleAiException.class, () ->
                guardrailService.validateUpload(List.of(slice), "CT_SERIES")
        );
        assertTrue(ex.getMessage().contains("Unsupported file") || ex.getMessage().contains("corrupt"),
                "Should reject non-image file: " + ex.getMessage());
        verifyNoInteractions(mockGeminiVerifier);
    }

    @Test
    @DisplayName("Oversized file (>25MB) is rejected before processing")
    void testOversizedFileRejected() {
        byte[] hugeBytes = new byte[26 * 1024 * 1024];
        SliceInput slice = new SliceInput(0, "huge.jpg", hugeBytes, "image/jpeg");

        ResponsibleAiException ex = assertThrows(ResponsibleAiException.class, () ->
                guardrailService.validateUpload(List.of(slice), "CT_SERIES")
        );
        assertTrue(ex.getMessage().contains("25MB"), "Should mention 25MB limit: " + ex.getMessage());
        verifyNoInteractions(mockGeminiVerifier);
    }

    @Test
    @DisplayName("Bag photo is rejected by Gemini gate with reason")
    void testBagPhotoRejectedByGeminiGate() throws Exception {
        byte[] imageBytes = createSyntheticImage(128, 128, new Color(180, 120, 80));
        SliceInput slice = new SliceInput(0, "handbag.jpg", imageBytes, "image/jpeg");

        when(mockGeminiVerifier.verify(any(), any(), eq("EXTERNAL_PHOTO")))
                .thenReturn(GeminiImageVerifier.ValidationResult.invalid("Image shows a leather handbag, not human skin"));

        ResponsibleAiException ex = assertThrows(ResponsibleAiException.class, () ->
                guardrailService.validateUpload(List.of(slice), "EXTERNAL_PHOTO")
        );
        assertTrue(ex.getMessage().contains("handbag"), "Should reflect Gemini rejection reason: " + ex.getMessage());
    }

    @Test
    @DisplayName("Valid skin photo accepted with mock Gemini")
    void testValidSkinPhotoAcceptedWithMockGemini() throws Exception {
        byte[] skinBytes = createSyntheticImage(128, 128, new Color(225, 175, 145));
        SliceInput slice = new SliceInput(0, "skin_mole.jpg", skinBytes, "image/jpeg");

        when(mockGeminiVerifier.verify(any(), any(), eq("EXTERNAL_PHOTO")))
                .thenReturn(GeminiImageVerifier.ValidationResult.valid());

        assertDoesNotThrow(() ->
                guardrailService.validateUpload(List.of(slice), "EXTERNAL_PHOTO")
        );
    }

    @Test
    @DisplayName("Valid CT scan accepted with mock Gemini")
    void testValidCtScanAcceptedWithMockGemini() throws Exception {
        byte[] ctBytes = createCtImage(128, 128);
        SliceInput slice = new SliceInput(0, "chest_ct_slice.jpg", ctBytes, "image/jpeg");

        when(mockGeminiVerifier.verify(any(), any(), eq("CT_SERIES")))
                .thenReturn(GeminiImageVerifier.ValidationResult.valid());

        assertDoesNotThrow(() ->
                guardrailService.validateUpload(List.of(slice), "CT_SERIES")
        );
    }

    @Test
    @DisplayName("Gemini call failure or timeout rejects with fallback message")
    void testGeminiFailureOrTimeoutRejects() throws Exception {
        byte[] ctBytes = createCtImage(128, 128);
        SliceInput slice = new SliceInput(0, "chest_ct_slice.jpg", ctBytes, "image/jpeg");

        when(mockGeminiVerifier.verify(any(), any(), any()))
                .thenReturn(GeminiImageVerifier.ValidationResult.invalid("Could not verify the image, please try again"));

        ResponsibleAiException ex = assertThrows(ResponsibleAiException.class, () ->
                guardrailService.validateUpload(List.of(slice), "CT_SERIES")
        );
        assertEquals("Could not verify the image, please try again", ex.getMessage());
    }

    @Test
    @DisplayName("Rejected upload in ScanService records audit log, does not consume quota or save scan")
    void testRejectedUploadDoesNotSaveScanOrConsumeQuota() throws Exception {
        ScanRepository scanRepo = mock(ScanRepository.class);
        AsyncPipelineExecutor asyncExec = mock(AsyncPipelineExecutor.class);
        AuditLogService auditLog = mock(AuditLogService.class);
        ImageStorageService imgStorage = mock(ImageStorageService.class);
        QuotaService quotaService = mock(QuotaService.class);
        ScanDeduplicationService dedup = mock(ScanDeduplicationService.class);

        ScanService scanService = new ScanService(
                scanRepo,
                asyncExec,
                auditLog,
                imgStorage,
                quotaService,
                dedup,
                guardrailService
        );

        UUID userId = UUID.randomUUID();
        byte[] textBytes = "non image file".getBytes();
        SliceInput invalidSlice = new SliceInput(0, "random.pdf", textBytes, "application/pdf");

        assertThrows(ResponsibleAiException.class, () ->
                scanService.submit(userId, List.of(invalidSlice), "EXTERNAL_PHOTO", null, null)
        );

        // Verification: audit log recorded, quota NOT consumed, scan NOT saved, async NOT run
        verify(auditLog).record(eq(userId), eq("UPLOAD_REJECTED"), any(UUID.class));
        verifyNoInteractions(quotaService);
        verifyNoInteractions(scanRepo);
        verifyNoInteractions(asyncExec);
        verifyNoInteractions(imgStorage);
    }
}
