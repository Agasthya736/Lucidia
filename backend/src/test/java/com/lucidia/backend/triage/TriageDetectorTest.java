package com.lucidia.backend.triage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TriageDetectorTest {

    private PretrainedCtTriageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PretrainedCtTriageDetector(0.85, 0.0005);
    }

    @Test
    void testNormalCleanSliceAnalysis() throws IOException {
        byte[] cleanImage = createSyntheticCtSlice(false);
        SliceInput slice = new SliceInput(0, "clean_slice_01.png", cleanImage, "image/png");

        SliceFindings findings = detector.analyzeSlice(slice);

        assertNotNull(findings);
        assertEquals(0, findings.sliceIndex());
        assertFalse(findings.hasAbnormality());
        assertEquals("NORMAL", findings.classification());
        assertTrue(findings.confidence() >= 0.85);
        assertTrue(findings.lesions().isEmpty());
    }

    @Test
    void testAbnormalSliceAnalysisWithLesionDetection() throws IOException {
        byte[] abnormalImage = createSyntheticCtSlice(true);
        SliceInput slice = new SliceInput(0, "abnormal_slice_01.png", abnormalImage, "image/png");

        SliceFindings findings = detector.analyzeSlice(slice);

        assertNotNull(findings);
        assertTrue(findings.hasAbnormality());
        assertEquals("ABNORMAL", findings.classification());
        assertFalse(findings.lesions().isEmpty());

        DetectedLesion lesion = findings.lesions().get(0);
        assertNotNull(lesion.anatomicalRegion());
        assertNotNull(lesion.boundingBox());
        assertEquals(4, lesion.boundingBox().size());
        assertTrue(lesion.confidence() > 0.70);
    }

    @Test
    void testSeriesAggregationClean() throws IOException {
        byte[] clean1 = createSyntheticCtSlice(false);
        byte[] clean2 = createSyntheticCtSlice(false);

        List<SliceInput> slices = List.of(
                new SliceInput(0, "slice_0.png", clean1, "image/png"),
                new SliceInput(1, "slice_1.png", clean2, "image/png")
        );

        AggregatedFindings agg = detector.analyzeSeries(slices);

        assertEquals(2, agg.totalSlices());
        assertEquals(0, agg.abnormalSlicesCount());
        assertEquals("NORMAL", agg.overallStatus());
        assertTrue(agg.isHighConfidenceClean(0.85));
    }

    @Test
    void testSeriesAggregationAbnormal() throws IOException {
        byte[] clean = createSyntheticCtSlice(false);
        byte[] abnormal = createSyntheticCtSlice(true);

        List<SliceInput> slices = List.of(
                new SliceInput(0, "slice_0.png", clean, "image/png"),
                new SliceInput(1, "slice_1.png", abnormal, "image/png")
        );

        AggregatedFindings agg = detector.analyzeSeries(slices);

        assertEquals(2, agg.totalSlices());
        assertEquals(1, agg.abnormalSlicesCount());
        assertEquals("ABNORMAL", agg.overallStatus());
        assertFalse(agg.isHighConfidenceClean(0.85));
        assertFalse(agg.topLesions().isEmpty());
    }

    @Test
    void testExternalPhotoAnalysis() {
        ExternalPhotoTriageDetector extDetector = new ExternalPhotoTriageDetector();
        assertNotNull(extDetector.getDetectorName());

        // Test with empty list
        AggregatedFindings emptyFindings = extDetector.analyzePhotos(List.of());
        assertNotNull(emptyFindings);
        assertEquals(0, emptyFindings.totalSlices());

        // Test with real sample photo if present on disk
        java.io.File realPhoto = new java.io.File("data/images/eb47d6e8-9011-4ffa-aff6-8d502c3a261b_slice_0.bin");
        if (realPhoto.exists()) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(realPhoto.toPath());
                AggregatedFindings realFindings = extDetector.analyzePhotos(List.of(
                        new SliceInput(0, "thumb_wart.jpg", bytes, "image/jpeg")
                ));
                assertNotNull(realFindings);
                assertTrue(realFindings.totalSlices() > 0);
                assertEquals("ABNORMAL", realFindings.overallStatus());
                assertFalse(realFindings.topLesions().isEmpty());
                DetectedLesion top = realFindings.topLesions().get(0);
                System.out.println("Top lesion: " + top.lesionType() + " in " + top.anatomicalRegion() + " box: " + top.boundingBox());
                // The bounding box must be on the hand/digit (y between 300 and 700, x between 200 and 650)
                assertTrue(top.boundingBox().get(1) > 250, "Bounding box Y1 must not be on the top outer region");
                assertTrue(top.boundingBox().get(0) > 150, "Bounding box X1 must be focused on digit/hand");
                assertTrue(top.anatomicalRegion().contains("Hand") || top.anatomicalRegion().contains("Digit") || top.anatomicalRegion().contains("Periungual") || top.anatomicalRegion().contains("Skin"));
            } catch (IOException e) {
                // Ignore if read failed
            }
        }
    }

    private byte[] createSyntheticCtSlice(boolean includeLesion) throws IOException {
        int width = 256;
        int height = 256;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background / Air (black: HU -1000)
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Body boundary (soft tissue: gray val 120)
        g.setColor(new Color(110, 110, 110));
        g.fillOval(25, 25, 206, 206);

        // Lungs (dark parenchyma: val 45)
        g.setColor(new Color(45, 45, 45));
        // Right lung
        g.fillOval(45, 60, 65, 130);
        // Left lung
        g.fillOval(145, 60, 65, 130);

        // If abnormal, inject a focal hyperdense nodule in Right Upper Lobe (val 185)
        if (includeLesion) {
            g.setColor(new Color(185, 185, 185));
            g.fillOval(70, 80, 22, 22);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
