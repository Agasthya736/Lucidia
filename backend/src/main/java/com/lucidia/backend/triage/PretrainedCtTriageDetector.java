package com.lucidia.backend.triage;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.vision.GeminiVisionAgent;
import com.lucidia.backend.agents.vision.VisionFindings;

/**
 * CT triage detector based on fixed brightness rules (no trained model).
 * - Finds the background air and the outer body edge so findings are never
 * placed on the skin line.
 * - Looks for dense areas touching the lung fields.
 * - Optionally asks Gemini for a second read.
 *
 * Honesty rules in this class:
 * - Images carry no real Hounsfield units or pixel spacing, so densityHu and
 * sizeMm are always 0.0
 * meaning "not measured". The UI and report must hide zero values.
 * - Confidence values are rough heuristic scores, not probabilities.
 * - If an image cannot be read or a step throws, the exception propagates so
 * the scan is marked FAILED.
 * This class never turns an error into a result.
 * - Free text from Gemini is never passed on. Region and description are built
 * from the box position.
 *
 * Boxes use a 0-1000 normalised scale [x1, y1, x2, y2].
 */
@Service
public class PretrainedCtTriageDetector implements TriageDetector {

    private static final Logger log = LoggerFactory.getLogger(PretrainedCtTriageDetector.class);

    // Rough heuristic scores. Keep them below the inconclusive threshold when the
    // evidence is weak.
    private static final double NO_FINDING_CONFIDENCE_WITH_SECOND_READ = 0.80;
    private static final double NO_FINDING_CONFIDENCE_HEURISTIC_ONLY = 0.65;
    private static final double MIN_LESION_CONFIDENCE = 0.55;
    private static final double MAX_LESION_CONFIDENCE = 0.90;

    private static final String SECOND_READ_RAN = "ran";
    private static final String SECOND_READ_FAILED = "failed";
    private static final String SECOND_READ_UNAVAILABLE = "unavailable";

    private final double confidenceThreshold;
    private final double inconclusiveThreshold;
    private final double minLesionAreaFraction;
    private final GeminiVisionAgent geminiVisionAgent;

    @Autowired
    public PretrainedCtTriageDetector(
            @Value("${lucidia.triage.confidence-threshold:0.85}") double confidenceThreshold,
            @Value("${lucidia.triage.inconclusive-threshold:0.70}") double inconclusiveThreshold,
            @Value("${lucidia.triage.min-lesion-area-fraction:0.0005}") double minLesionAreaFraction,
            @Autowired(required = false) GeminiVisionAgent geminiVisionAgent) {
        this.confidenceThreshold = confidenceThreshold;
        this.inconclusiveThreshold = inconclusiveThreshold;
        this.minLesionAreaFraction = minLesionAreaFraction;
        this.geminiVisionAgent = geminiVisionAgent;
    }

    public PretrainedCtTriageDetector(double confidenceThreshold, double minLesionAreaFraction) {
        this(confidenceThreshold, 0.70, minLesionAreaFraction, null);
    }

    @Override
    public String getDetectorName() {
        return "Lucidia CT triage v2.1 (rule-based lung-field and dense-area detector)";
    }

    @Override
    public AggregatedFindings analyzeSeries(List<SliceInput> slices) {
        if (slices == null || slices.isEmpty()) {
            return AggregatedFindings.fromSliceFindings(List.of(), inconclusiveThreshold);
        }

        List<SliceFindings> sliceFindings = new ArrayList<>();
        for (SliceInput slice : slices) {
            sliceFindings.add(analyzeSlice(slice));
        }

        return AggregatedFindings.fromSliceFindings(sliceFindings, inconclusiveThreshold);
    }

    @Override
    public SliceFindings analyzeSlice(SliceInput slice) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(slice.bytes()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read image for slice " + slice.sliceIndex(), e);
        }
        if (image == null) {
            throw new IllegalStateException("Could not decode image for slice " + slice.sliceIndex());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        Raster raster = image.getData();

        int totalPixels = width * height;
        long sumLuminance = 0;
        int lungParenchymaCount = 0;

        int[] pixelBuffer = new int[4];
        int[][] grayscale = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, pixelBuffer);
                int gray;
                if (raster.getNumBands() >= 3 && image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                    gray = (int) (0.299 * pixelBuffer[0] + 0.587 * pixelBuffer[1] + 0.114 * pixelBuffer[2]);
                } else {
                    gray = pixelBuffer[0];
                }
                grayscale[y][x] = gray;
                sumLuminance += gray;

                if (gray > 20 && gray < 100) {
                    lungParenchymaCount++;
                }
            }
        }

        double meanLuminance = (double) sumLuminance / totalPixels;
        double lungFraction = (double) lungParenchymaCount / totalPixels;

        // 1. Rule-based detection
        List<DetectedLesion> lesions = detectAnomalies(grayscale, width, height);

        // 2. Optional Gemini second read. Failures are recorded, never hidden.
        String secondRead = SECOND_READ_UNAVAILABLE;
        if (geminiVisionAgent != null && slice.bytes() != null && slice.bytes().length > 0) {
            try {
                String mime = slice.mimeType() != null ? slice.mimeType() : "image/jpeg";
                VisionFindings aiFindings = geminiVisionAgent.analyze(slice.bytes(), mime);
                if (aiFindings == null) {
                    secondRead = SECOND_READ_FAILED;
                    log.warn("Gemini second read returned nothing for slice {}", slice.sliceIndex());
                } else {
                    secondRead = SECOND_READ_RAN;
                    lesions = mergeSecondRead(lesions, aiFindings);
                }
            } catch (Exception e) {
                secondRead = SECOND_READ_FAILED;
                log.warn("Gemini second read failed for slice {}: {}", slice.sliceIndex(), e.getMessage());
            }
        }

        boolean hasAbnormality = !lesions.isEmpty();
        String classification = hasAbnormality ? "FINDINGS_DETECTED" : "NO_FINDINGS_DETECTED";

        double confidence;
        if (hasAbnormality) {
            double maxConf = 0.0;
            for (DetectedLesion l : lesions) {
                if (l.confidence() > maxConf)
                    maxConf = l.confidence();
            }
            confidence = maxConf;
        } else {
            confidence = SECOND_READ_RAN.equals(secondRead)
                    ? NO_FINDING_CONFIDENCE_WITH_SECOND_READ
                    : NO_FINDING_CONFIDENCE_HEURISTIC_ONLY;
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("meanLuminance", Math.round(meanLuminance * 100.0) / 100.0);
        metrics.put("lungFraction", Math.round(lungFraction * 1000.0) / 1000.0);
        metrics.put("lesionCount", lesions.size());
        metrics.put("imageResolution", width + "x" + height);
        metrics.put("secondRead", secondRead);

        return new SliceFindings(
                slice.sliceIndex(),
                slice.filename(),
                hasAbnormality,
                classification,
                confidence,
                Collections.unmodifiableList(lesions),
                Collections.unmodifiableMap(metrics));
    }

    /**
     * Combines the Gemini box with the rule-based findings.
     * Only the box position and confidence are used from Gemini; its text is
     * ignored.
     */
    private List<DetectedLesion> mergeSecondRead(List<DetectedLesion> lesions, VisionFindings aiFindings) {
        int[] b = aiFindings.boundingBox();
        if (b == null || b.length != 4) {
            return lesions;
        }

        int rawX1 = Math.max(0, Math.min(b[0], b[2]));
        int rawY1 = Math.max(0, Math.min(b[1], b[3]));
        int rawX2 = Math.min(1000, Math.max(b[0], b[2]));
        int rawY2 = Math.min(1000, Math.max(b[1], b[3]));
        int boxW = rawX2 - rawX1;
        int boxH = rawY2 - rawY1;

        if (boxW < 15 || boxH < 15) {
            return lesions;
        }

        // Tighten very large boxes around their centre so they do not cover heart, ribs
        // or other tissue
        int tightX1 = rawX1;
        int tightY1 = rawY1;
        int tightX2 = rawX2;
        int tightY2 = rawY2;
        if (boxW > 220) {
            int cx = (rawX1 + rawX2) / 2;
            tightX1 = Math.max(0, cx - 100);
            tightX2 = Math.min(1000, cx + 100);
        }
        if (boxH > 220) {
            int cy = (rawY1 + rawY2) / 2;
            tightY1 = Math.max(0, cy - 100);
            tightY2 = Math.min(1000, cy + 100);
        }
        List<Integer> aiBox = List.of(tightX1, tightY1, tightX2, tightY2);

        String region = mapAnatomicalRegion(tightX1, tightY1, tightX2, tightY2, 1000, 1000);
        if ("Mediastinum / Central Airway".equals(region)) {
            return lesions; // central structures are not reported
        }

        double aiConfidence = Math.max(0.0, Math.min(1.0, aiFindings.confidence()));
        log.info("Gemini second read suggests a focal area in {} with box {}", region, aiBox);

        List<DetectedLesion> merged = new ArrayList<>();
        boolean absorbed = false;

        for (DetectedLesion ml : lesions) {
            if (hasSignificantOverlap(aiBox, ml.boundingBox())) {
                // Same area found twice: keep the rule-based type and description, tighter box,
                // higher score
                merged.add(new DetectedLesion(
                        ml.lesionType(),
                        ml.anatomicalRegion(),
                        getTighterBox(aiBox, ml.boundingBox()),
                        Math.max(aiConfidence, ml.confidence()),
                        0.0,
                        0.0,
                        ml.description()));
                absorbed = true;
            } else {
                merged.add(ml);
            }
        }

        if (!absorbed) {
            merged.add(0, new DetectedLesion(
                    "Focal pulmonary lesion",
                    region,
                    aiBox,
                    aiConfidence,
                    0.0,
                    0.0,
                    "A dense-looking area in the " + region));
        }

        return deduplicateOverlaps(merged);
    }

    private List<DetectedLesion> detectAnomalies(int[][] gray, int width, int height) {
        // Step 1: background air outside the body (connected to the image border, dark)
        boolean[][] isBackgroundAir = new boolean[height][width];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            if (gray[0][x] < 35 && !isBackgroundAir[0][x]) {
                isBackgroundAir[0][x] = true;
                queue.add(new int[] { x, 0 });
            }
            if (gray[height - 1][x] < 35 && !isBackgroundAir[height - 1][x]) {
                isBackgroundAir[height - 1][x] = true;
                queue.add(new int[] { x, height - 1 });
            }
        }
        for (int y = 0; y < height; y++) {
            if (gray[y][0] < 35 && !isBackgroundAir[y][0]) {
                isBackgroundAir[y][0] = true;
                queue.add(new int[] { 0, y });
            }
            if (gray[y][width - 1] < 35 && !isBackgroundAir[y][width - 1]) {
                isBackgroundAir[y][width - 1] = true;
                queue.add(new int[] { width - 1, y });
            }
        }

        int[] dx = { -1, 1, 0, 0 };
        int[] dy = { 0, 0, -1, 1 };
        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int cx = pt[0];
            int cy = pt[1];
            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (!isBackgroundAir[ny][nx] && gray[ny][nx] < 35) {
                        isBackgroundAir[ny][nx] = true;
                        queue.add(new int[] { nx, ny });
                    }
                }
            }
        }

        // Step 2: outer body edge. Findings are never placed there.
        int[][] distToAir = new int[height][width];
        int maxDist = 999;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                distToAir[y][x] = isBackgroundAir[y][x] ? 0 : maxDist;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (distToAir[y][x] > 0) {
                    if (y > 0)
                        distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y - 1][x] + 1);
                    if (x > 0)
                        distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y][x - 1] + 1);
                }
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (distToAir[y][x] > 0) {
                    if (y < height - 1)
                        distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y + 1][x] + 1);
                    if (x < width - 1)
                        distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y][x + 1] + 1);
                }
            }
        }

        int boundaryMargin = Math.max(12, (int) (Math.min(width, height) * 0.05));
        boolean[][] isBodyBoundary = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isBackgroundAir[y][x] || distToAir[y][x] <= boundaryMargin) {
                    isBodyBoundary[y][x] = true;
                }
            }
        }

        // Step 3: lung field (dark interior area)
        boolean[][] isParenchyma = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isBodyBoundary[y][x] && gray[y][x] >= 20 && gray[y][x] <= 95) {
                    isParenchyma[y][x] = true;
                }
            }
        }

        // Step 4: dense areas touching the lung field with strong contrast
        int minBoxSize = Math.max(10, (int) (Math.min(width, height) * 0.03));
        int maxBoxSize = (int) (Math.min(width, height) * 0.22);

        boolean[][] visited = new boolean[height][width];
        List<CandidateLesion> candidates = new ArrayList<>();

        for (int y = boundaryMargin; y < height - boundaryMargin; y += 3) {
            for (int x = boundaryMargin; x < width - boundaryMargin; x += 3) {
                if (visited[y][x] || isBodyBoundary[y][x])
                    continue;

                int val = gray[y][x];
                if (val >= 125 && val <= 235) {
                    int parenchymaCount = 0;
                    int parenchymaSum = 0;
                    int checkRadius = Math.max(8, (int) (Math.min(width, height) * 0.04));

                    for (int ddy = -checkRadius; ddy <= checkRadius; ddy += 2) {
                        for (int ddx = -checkRadius; ddx <= checkRadius; ddx += 2) {
                            int ny = y + ddy;
                            int nx = x + ddx;
                            if (ny >= 0 && ny < height && nx >= 0 && nx < width && isParenchyma[ny][nx]) {
                                parenchymaCount++;
                                parenchymaSum += gray[ny][nx];
                            }
                        }
                    }

                    if (parenchymaCount >= 6) {
                        double meanParenchyma = (double) parenchymaSum / parenchymaCount;
                        double contrast = val - meanParenchyma;

                        if (contrast >= 40.0) {
                            int bX1 = x, bX2 = x, bY1 = y, bY2 = y;
                            int lesionPixels = 0;
                            long pixelSum = 0;

                            Queue<int[]> lesionQueue = new ArrayDeque<>();
                            lesionQueue.add(new int[] { x, y });
                            visited[y][x] = true;

                            int maxExpansionRadius = (int) (Math.min(width, height) * 0.08);
                            while (!lesionQueue.isEmpty()) {
                                int[] p = lesionQueue.poll();
                                int px = p[0];
                                int py = p[1];

                                lesionPixels++;
                                pixelSum += gray[py][px];
                                bX1 = Math.min(bX1, px);
                                bX2 = Math.max(bX2, px);
                                bY1 = Math.min(bY1, py);
                                bY2 = Math.max(bY2, py);

                                for (int i = 0; i < 4; i++) {
                                    int nx = px + dx[i];
                                    int ny = py + dy[i];
                                    if (nx >= 0 && nx < width && ny >= 0 && ny < height
                                            && !visited[ny][nx] && !isBodyBoundary[ny][nx]
                                            && Math.abs(nx - x) <= maxExpansionRadius
                                            && Math.abs(ny - y) <= maxExpansionRadius) {
                                        int nval = gray[ny][nx];
                                        if (nval >= 125 && nval <= 235 && Math.abs(nval - val) <= 30) {
                                            visited[ny][nx] = true;
                                            lesionQueue.add(new int[] { nx, ny });
                                        }
                                    }
                                }
                            }

                            int bW = bX2 - bX1;
                            int bH = bY2 - bY1;
                            double fillRatio = (double) lesionPixels / Math.max(1, bW * bH);
                            if (bW >= minBoxSize && bH >= minBoxSize && bW <= maxBoxSize && bH <= maxBoxSize
                                    && lesionPixels >= 25 && fillRatio >= 0.20) {
                                double score = lesionPixels * contrast * Math.min(parenchymaCount, 50);
                                candidates.add(new CandidateLesion(bX1, bY1, bX2, bY2, lesionPixels, score));
                            }
                        }
                    }
                }
            }
        }

        candidates.sort((c1, c2) -> Double.compare(c2.score, c1.score));

        List<DetectedLesion> result = new ArrayList<>();
        for (CandidateLesion c : candidates) {
            boolean overlaps = false;
            for (DetectedLesion existing : result) {
                int ex1 = (int) Math.round((existing.boundingBox().get(0) / 1000.0) * width);
                int ey1 = (int) Math.round((existing.boundingBox().get(1) / 1000.0) * height);
                int ex2 = (int) Math.round((existing.boundingBox().get(2) / 1000.0) * width);
                int ey2 = (int) Math.round((existing.boundingBox().get(3) / 1000.0) * height);

                int interX1 = Math.max(c.x1, ex1);
                int interY1 = Math.max(c.y1, ey1);
                int interX2 = Math.min(c.x2, ex2);
                int interY2 = Math.min(c.y2, ey2);

                if (interX2 > interX1 && interY2 > interY1) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps)
                continue;

            String region = mapAnatomicalRegion(c.x1, c.y1, c.x2, c.y2, width, height);
            if ("Mediastinum / Central Airway".equals(region)) {
                continue;
            }

            double maxDimPx = Math.max(c.x2 - c.x1, c.y2 - c.y1);
            double relativeSize = maxDimPx / Math.min(width, height);
            String sizeWord = relativeSize < 0.06 ? "small" : (relativeSize < 0.12 ? "medium-sized" : "larger");

            // Relative size only. Real millimetres cannot be known from a photo or export.
            String lesionType = relativeSize < 0.06 ? "Pulmonary nodule" : "Focal consolidation / opacity";
            String shape = "Pulmonary nodule".equals(lesionType) ? "rounded dense area" : "cloudy dense area";
            String desc = "A " + sizeWord + " " + shape + " in the " + region;

            List<Integer> normBox = List.of(
                    scaleTo1000(c.x1, width),
                    scaleTo1000(c.y1, height),
                    scaleTo1000(c.x2, width),
                    scaleTo1000(c.y2, height));

            double conf = MIN_LESION_CONFIDENCE
                    + Math.min(1.0, c.lesionPixels / 300.0) * (MAX_LESION_CONFIDENCE - MIN_LESION_CONFIDENCE);
            conf = Math.round(conf * 100.0) / 100.0;

            result.add(new DetectedLesion(lesionType, region, normBox, conf, 0.0, 0.0, desc));

            if (result.size() >= 3)
                break;
        }

        return result;
    }

    private static class CandidateLesion {
        final int x1, y1, x2, y2, lesionPixels;
        final double score;

        CandidateLesion(int x1, int y1, int x2, int y2, int lesionPixels, double score) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.lesionPixels = lesionPixels;
            this.score = score;
        }
    }

    private String mapAnatomicalRegion(int x1, int y1, int x2, int y2, int width, int height) {
        double centerX = (x1 + x2) / 2.0 / width;
        double centerY = (y1 + y2) / 2.0 / height;

        // Screen left is the patient's right, screen right is the patient's left
        if (centerX > 0.40 && centerX < 0.60 && centerY > 0.35 && centerY < 0.65) {
            return "Mediastinum / Central Airway";
        } else if (centerX <= 0.50) {
            if (centerY < 0.45) {
                return "Right Upper Lobe";
            } else if (centerY < 0.65) {
                return "Right Middle Lobe";
            } else {
                return "Right Lower Lobe";
            }
        } else {
            if (centerY < 0.50) {
                return "Left Upper Lobe / Lingula";
            } else {
                return "Left Lower Lobe";
            }
        }
    }

    private int scaleTo1000(int value, int total) {
        return (int) Math.round(((double) value / total) * 1000.0);
    }

    private boolean hasSignificantOverlap(List<Integer> b1, List<Integer> b2) {
        if (b1 == null || b2 == null || b1.size() != 4 || b2.size() != 4)
            return false;
        int x1 = Math.max(b1.get(0), b2.get(0));
        int y1 = Math.max(b1.get(1), b2.get(1));
        int x2 = Math.min(b1.get(2), b2.get(2));
        int y2 = Math.min(b1.get(3), b2.get(3));
        if (x2 <= x1 || y2 <= y1)
            return false;

        int interArea = (x2 - x1) * (y2 - y1);
        int area1 = (b1.get(2) - b1.get(0)) * (b1.get(3) - b1.get(1));
        int area2 = (b2.get(2) - b2.get(0)) * (b2.get(3) - b2.get(1));
        int minArea = Math.min(area1, area2);
        return minArea > 0 && ((double) interArea / minArea) > 0.30;
    }

    private List<Integer> getTighterBox(List<Integer> b1, List<Integer> b2) {
        int area1 = (b1.get(2) - b1.get(0)) * (b1.get(3) - b1.get(1));
        int area2 = (b2.get(2) - b2.get(0)) * (b2.get(3) - b2.get(1));
        return area1 <= area2 ? b1 : b2;
    }

    private List<DetectedLesion> deduplicateOverlaps(List<DetectedLesion> list) {
        List<DetectedLesion> clean = new ArrayList<>();
        for (DetectedLesion item : list) {
            if (clean.size() >= 3)
                break;
            boolean overlaps = false;
            for (DetectedLesion existing : clean) {
                if (hasSignificantOverlap(item.boundingBox(), existing.boundingBox())) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                clean.add(item);
            }
        }
        return clean;
    }
}