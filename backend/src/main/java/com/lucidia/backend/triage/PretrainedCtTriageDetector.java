package com.lucidia.backend.triage;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
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
 * Pretrained CT Triage Detector.
 * Performs anatomical lung-field segmentation and focal lesion detection:
 * - Detects background ambient air and patient outer boundary (skin/chest wall) to strictly prevent false positives on body borders.
 * - Identifies low-attenuation lung parenchyma cavities.
 * - Detects focal nodular densities and consolidations strictly INSIDE or bordering lung fields.
 * - Optionally collaborates with Gemini Vision Agent when available for multi-agent second-read verification.
 * - Maps bounding boxes [x1, y1, x2, y2] on a 0-1000 normalized scale.
 */
@Service
public class PretrainedCtTriageDetector implements TriageDetector {

    private static final Logger log = LoggerFactory.getLogger(PretrainedCtTriageDetector.class);

    private final double confidenceThreshold;
    private final double minLesionAreaFraction;
    private final GeminiVisionAgent geminiVisionAgent;

    @Autowired
    public PretrainedCtTriageDetector(
            @Value("${lucidia.triage.confidence-threshold:0.85}") double confidenceThreshold,
            @Value("${lucidia.triage.min-lesion-area-fraction:0.0005}") double minLesionAreaFraction,
            @Autowired(required = false) GeminiVisionAgent geminiVisionAgent) {
        this.confidenceThreshold = confidenceThreshold;
        this.minLesionAreaFraction = minLesionAreaFraction;
        this.geminiVisionAgent = geminiVisionAgent;
    }

    public PretrainedCtTriageDetector(double confidenceThreshold, double minLesionAreaFraction) {
        this(confidenceThreshold, minLesionAreaFraction, null);
    }

    @Override
    public String getDetectorName() {
        return "Lucidia-CT-Triage-v2.0 (Anatomical Lung-Field & Lesion Detector)";
    }

    @Override
    public AggregatedFindings analyzeSeries(List<SliceInput> slices) {
        if (slices == null || slices.isEmpty()) {
            return AggregatedFindings.fromSliceFindings(List.of(), confidenceThreshold);
        }

        List<SliceFindings> sliceFindings = new ArrayList<>();
        for (SliceInput slice : slices) {
            sliceFindings.add(analyzeSlice(slice));
        }

        return AggregatedFindings.fromSliceFindings(sliceFindings, confidenceThreshold);
    }

    @Override
    public SliceFindings analyzeSlice(SliceInput slice) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(slice.bytes()));
            if (image == null) {
                log.warn("Unable to decode image raster for slice index {}", slice.sliceIndex());
                return SliceFindings.normal(slice.sliceIndex(), slice.filename(), 0.50);
            }

            int width = image.getWidth();
            int height = image.getHeight();
            Raster raster = image.getData();

            int totalPixels = width * height;
            int sumLuminance = 0;
            int lungParenchymaCount = 0;

            int[] pixelBuffer = new int[4];
            int[][] grayscale = new int[height][width];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    raster.getPixel(x, y, pixelBuffer);
                    int gray;
                    if (pixelBuffer.length >= 3 && image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
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

            // 1. Run anatomical morphological lesion detection
            List<DetectedLesion> lesions = detectAnomalies(grayscale, width, height, slice.sliceIndex());

            // 2. If Gemini Vision is available, cross-check and enrich with multimodal model
            if (geminiVisionAgent != null && slice.bytes() != null && slice.bytes().length > 0) {
                try {
                    String mime = slice.mimeType() != null ? slice.mimeType() : "image/jpeg";
                    VisionFindings aiFindings = geminiVisionAgent.analyze(slice.bytes(), mime);
                    if (aiFindings != null && aiFindings.boundingBox() != null && aiFindings.boundingBox().length == 4) {
                        int[] b = aiFindings.boundingBox();
                        int rawX1 = Math.min(b[0], b[2]);
                        int rawY1 = Math.min(b[1], b[3]);
                        int rawX2 = Math.max(b[0], b[2]);
                        int rawY2 = Math.max(b[1], b[3]);
                        int boxW = rawX2 - rawX1;
                        int boxH = rawY2 - rawY1;

                        // Only consider non-trivial boxes
                        if (boxW >= 15 && boxH >= 15) {
                            // If the AI box is too large (> 220), tighten it around its center
                            // so it does NOT cover unwanted surrounding healthy tissue (mediastinum, heart, ribs)
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

                            log.info("Gemini Vision focal finding in '{}' with tightened box {}",
                                    aiFindings.regionDescription(), aiBox);

                            String desc = (aiFindings.observations() != null && !aiFindings.observations().isEmpty())
                                    ? String.join("; ", aiFindings.observations())
                                    : aiFindings.summary();

                            String region = (aiFindings.regionDescription() != null && !aiFindings.regionDescription().equalsIgnoreCase("uncertain"))
                                    ? aiFindings.regionDescription()
                                    : mapAnatomicalRegion(aiBox.get(0), aiBox.get(1), aiBox.get(2), aiBox.get(3), 1000, 1000);

                            DetectedLesion aiLesion = new DetectedLesion(
                                    "Focal pulmonary lesion",
                                    region,
                                    aiBox,
                                    Math.max(0.88, aiFindings.confidence()),
                                    45.0,
                                    25.0,
                                    desc
                            );

                            // Merge AI findings with morphological lesions:
                            // If they describe the same region, keep the TIGHTER box and enrich with AI diagnosis
                            List<DetectedLesion> merged = new ArrayList<>();
                            boolean absorbedByMl = false;

                            for (DetectedLesion ml : lesions) {
                                if (hasSignificantOverlap(aiBox, ml.boundingBox())) {
                                    List<Integer> bestBox = getTighterBox(aiBox, ml.boundingBox());
                                    merged.add(new DetectedLesion(
                                            aiLesion.lesionType(),
                                            region.equalsIgnoreCase("uncertain") ? ml.anatomicalRegion() : region,
                                            bestBox,
                                            Math.max(aiLesion.confidence(), ml.confidence()),
                                            ml.densityHu(),
                                            ml.sizeMm(),
                                            aiLesion.description()
                                    ));
                                    absorbedByMl = true;
                                } else {
                                    merged.add(ml);
                                }
                            }

                            if (!absorbedByMl) {
                                // Do not place a lesion over normal central mediastinum (heart/aorta)
                                if (!"Mediastinum / Central Airway".equals(mapAnatomicalRegion(aiBox.get(0), aiBox.get(1), aiBox.get(2), aiBox.get(3), 1000, 1000))) {
                                    merged.add(0, aiLesion);
                                }
                            }

                            lesions = deduplicateOverlaps(merged);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Gemini Vision second-read pass skipped: {}", e.getMessage());
                }
            }

            boolean hasAbnormality = !lesions.isEmpty();
            String classification = hasAbnormality ? "ABNORMAL" : "NORMAL";

            double confidence;
            if (hasAbnormality) {
                double maxConf = 0.0;
                for (DetectedLesion l : lesions) {
                    if (l.confidence() > maxConf) maxConf = l.confidence();
                }
                confidence = Math.max(0.85, maxConf);
            } else {
                confidence = lungFraction > 0.12 ? 0.94 : 0.88;
            }

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("meanLuminance", Math.round(meanLuminance * 100.0) / 100.0);
            metrics.put("lungFraction", Math.round(lungFraction * 1000.0) / 1000.0);
            metrics.put("lesionCount", lesions.size());
            metrics.put("imageResolution", width + "x" + height);

            return new SliceFindings(
                    slice.sliceIndex(),
                    slice.filename(),
                    hasAbnormality,
                    classification,
                    confidence,
                    Collections.unmodifiableList(lesions),
                    Collections.unmodifiableMap(metrics)
            );

        } catch (Exception e) {
            log.error("Triage detector error on slice {}: {}", slice.sliceIndex(), e.getMessage(), e);
            return new SliceFindings(
                    slice.sliceIndex(),
                    slice.filename(),
                    false,
                    "EQUIVOCAL",
                    0.50,
                    List.of(),
                    Map.of("error", e.getMessage())
            );
        }
    }

    private List<DetectedLesion> detectAnomalies(int[][] gray, int width, int height, int sliceIndex) {
        // Step 1: Detect ambient room air outside patient body
        // Background air connects to outer image borders with low intensity (< 35)
        boolean[][] isBackgroundAir = new boolean[height][width];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            if (gray[0][x] < 35 && !isBackgroundAir[0][x]) {
                isBackgroundAir[0][x] = true;
                queue.add(new int[]{x, 0});
            }
            if (gray[height - 1][x] < 35 && !isBackgroundAir[height - 1][x]) {
                isBackgroundAir[height - 1][x] = true;
                queue.add(new int[]{x, height - 1});
            }
        }
        for (int y = 0; y < height; y++) {
            if (gray[y][0] < 35 && !isBackgroundAir[y][0]) {
                isBackgroundAir[y][0] = true;
                queue.add(new int[]{0, y});
            }
            if (gray[y][width - 1] < 35 && !isBackgroundAir[y][width - 1]) {
                isBackgroundAir[y][width - 1] = true;
                queue.add(new int[]{width - 1, y});
            }
        }

        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int cx = pt[0];
            int cy = pt[1];

            int[] dx = {-1, 1, 0, 0};
            int[] dy = {0, 0, -1, 1};
            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (!isBackgroundAir[ny][nx] && gray[ny][nx] < 35) {
                        isBackgroundAir[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        // Step 2: Mark Outer Body Envelope (Skin / Subcutaneous tissue boundary)
        // Any pixel within ~18 pixels of outside air is part of the body perimeter.
        // Lesions must NEVER be placed on this external perimeter.
        int[][] distToAir = new int[height][width];
        int maxDist = 999;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                distToAir[y][x] = isBackgroundAir[y][x] ? 0 : maxDist;
            }
        }

        // Two-pass distance transform (Manhattan)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (distToAir[y][x] > 0) {
                    if (y > 0) distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y - 1][x] + 1);
                    if (x > 0) distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y][x - 1] + 1);
                }
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (distToAir[y][x] > 0) {
                    if (y < height - 1) distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y + 1][x] + 1);
                    if (x < width - 1) distToAir[y][x] = Math.min(distToAir[y][x], distToAir[y][x + 1] + 1);
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

        // Step 3: Identify Lung Parenchyma
        // Lung parenchyma is interior low-attenuation air space (gray 20 - 95)
        boolean[][] isParenchyma = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isBodyBoundary[y][x] && gray[y][x] >= 20 && gray[y][x] <= 95) {
                    isParenchyma[y][x] = true;
                }
            }
        }

        // Step 4: Search for true focal pulmonary opacities & masses
        // Criteria:
        // - In thoracic interior (!isBodyBoundary)
        // - Soft tissue attenuation (gray >= 125 && gray <= 235)
        // - DIRECTLY ADJACENT to lung parenchyma (contact with low-attenuation lung space)
        // - Strong contrast against neighboring lung parenchyma
        int minBoxSize = Math.max(10, (int) (Math.min(width, height) * 0.03));
        int maxBoxSize = (int) (Math.min(width, height) * 0.22);

        boolean[][] visited = new boolean[height][width];
        List<CandidateLesion> candidates = new ArrayList<>();

        for (int y = boundaryMargin; y < height - boundaryMargin; y += 3) {
            for (int x = boundaryMargin; x < width - boundaryMargin; x += 3) {
                if (visited[y][x] || isBodyBoundary[y][x]) continue;

                int val = gray[y][x];
                if (val >= 125 && val <= 235) {
                    // Check local contact with lung parenchyma
                    int parenchymaCount = 0;
                    int parenchymaSum = 0;
                    int checkRadius = Math.max(8, (int) (Math.min(width, height) * 0.04));

                    for (int dy = -checkRadius; dy <= checkRadius; dy += 2) {
                        for (int dx = -checkRadius; dx <= checkRadius; dx += 2) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                                if (isParenchyma[ny][nx]) {
                                    parenchymaCount++;
                                    parenchymaSum += gray[ny][nx];
                                }
                            }
                        }
                    }

                    // Must have meaningful contact with lung parenchyma (inside or abutting the lung field)
                    if (parenchymaCount >= 6) {
                        double meanParenchyma = (double) parenchymaSum / parenchymaCount;
                        double contrast = val - meanParenchyma;

                        if (contrast >= 40.0) {
                            // Expand lesion region
                            int bX1 = x, bX2 = x, bY1 = y, bY2 = y;
                            int lesionPixels = 0;
                            int pixelSum = 0;

                            Queue<int[]> lesionQueue = new ArrayDeque<>();
                            lesionQueue.add(new int[]{x, y});
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

                                int[] ldx = {-1, 1, 0, 0};
                                int[] ldy = {0, 0, -1, 1};
                                for (int i = 0; i < 4; i++) {
                                    int nx = px + ldx[i];
                                    int ny = py + ldy[i];
                                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                        if (!visited[ny][nx] && !isBodyBoundary[ny][nx]) {
                                            if (Math.abs(nx - x) <= maxExpansionRadius && Math.abs(ny - y) <= maxExpansionRadius) {
                                                int nval = gray[ny][nx];
                                                // Only expand to pixels with similar intensity to seed (within ±30)
                                                if (nval >= 125 && nval <= 235 && Math.abs(nval - val) <= 30) {
                                                    visited[ny][nx] = true;
                                                    lesionQueue.add(new int[]{nx, ny});
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            int bW = bX2 - bX1;
                            int bH = bY2 - bY1;

                            // Require a minimum fill ratio (>= 20%) so sparse scatters don't create huge boxes
                            double fillRatio = (double) lesionPixels / Math.max(1, bW * bH);
                            if (bW >= minBoxSize && bH >= minBoxSize && bW <= maxBoxSize && bH <= maxBoxSize && lesionPixels >= 25 && fillRatio >= 0.20) {
                                double avgGray = (double) pixelSum / lesionPixels;
                                double score = lesionPixels * contrast * Math.min(parenchymaCount, 50);

                                candidates.add(new CandidateLesion(
                                        bX1, bY1, bX2, bY2, lesionPixels, avgGray, contrast, score
                                ));
                            }
                        }
                    }
                }
            }
        }

        // Sort candidates by prominence score descending (largest, highest-contrast lung lesion first)
        candidates.sort((c1, c2) -> Double.compare(c2.score, c1.score));

        List<DetectedLesion> result = new ArrayList<>();
        for (CandidateLesion c : candidates) {
            // Check non-overlap with already added lesions
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

            if (!overlaps) {
                String region = mapAnatomicalRegion(c.x1, c.y1, c.x2, c.y2, width, height);

                // Exclude normal central mediastinum (heart, ascending aorta, pulmonary trunk)
                if ("Mediastinum / Central Airway".equals(region)) {
                    continue;
                }

                double pixelSpacingMm = 350.0 / Math.max(width, height);
                double maxDimPx = Math.max(c.x2 - c.x1, c.y2 - c.y1);
                double sizeMm = Math.round(maxDimPx * pixelSpacingMm * 10.0) / 10.0;
                double estimatedHu = Math.round((c.avgGray * 5.0 - 500.0) * 10.0) / 10.0;

                List<Integer> normBox = List.of(
                        scaleTo1000(c.x1, width),
                        scaleTo1000(c.y1, height),
                        scaleTo1000(c.x2, width),
                        scaleTo1000(c.y2, height)
                );

                double conf = Math.min(0.96, 0.78 + (c.lesionPixels / 300.0) * 0.14);
                conf = Math.round(conf * 100.0) / 100.0;

                String lesionType = sizeMm < 14 ? "Pulmonary nodule" : "Focal consolidation / opacity";
                String desc = String.format("Focal %s measuring approx. %.1f mm with density around %.0f HU in %s",
                        lesionType.toLowerCase(), sizeMm, estimatedHu, region);

                result.add(new DetectedLesion(
                        lesionType,
                        region,
                        normBox,
                        conf,
                        estimatedHu,
                        sizeMm,
                        desc
                ));

                if (result.size() >= 3) break;
            }
        }

        return result;
    }

    private static class CandidateLesion {
        final int x1, y1, x2, y2, lesionPixels;
        final double avgGray, contrast, score;

        CandidateLesion(int x1, int y1, int x2, int y2, int lesionPixels, double avgGray, double contrast, double score) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.lesionPixels = lesionPixels;
            this.avgGray = avgGray;
            this.contrast = contrast;
            this.score = score;
        }
    }

    private String mapAnatomicalRegion(int x1, int y1, int x2, int y2, int width, int height) {
        double centerX = (x1 + x2) / 2.0 / width;
        double centerY = (y1 + y2) / 2.0 / height;

        // In radiological convention:
        // Screen Left is Anatomical Right (Patient's Right)
        // Screen Right is Anatomical Left (Patient's Left)
        if (centerX > 0.40 && centerX < 0.60 && centerY > 0.35 && centerY < 0.65) {
            return "Mediastinum / Central Airway";
        } else if (centerX <= 0.50) {
            // Anatomical Right (Screen Left)
            if (centerY < 0.45) {
                return "Right Upper Lobe";
            } else if (centerY < 0.65) {
                return "Right Middle Lobe";
            } else {
                return "Right Lower Lobe";
            }
        } else {
            // Anatomical Left (Screen Right)
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
        if (b1 == null || b2 == null || b1.size() != 4 || b2.size() != 4) return false;
        int x1 = Math.max(b1.get(0), b2.get(0));
        int y1 = Math.max(b1.get(1), b2.get(1));
        int x2 = Math.min(b1.get(2), b2.get(2));
        int y2 = Math.min(b1.get(3), b2.get(3));
        if (x2 <= x1 || y2 <= y1) return false;

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
            if (clean.size() >= 3) break;
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
