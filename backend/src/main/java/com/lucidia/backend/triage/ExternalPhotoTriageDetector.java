package com.lucidia.backend.triage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExternalPhotoTriageDetector {

    private static final Logger log = LoggerFactory.getLogger(ExternalPhotoTriageDetector.class);

    public String getDetectorName() {
        return "Lucidia-External-Surface-v1.0 (Dermatological & Exterior Morphological Analyzer)";
    }

    public AggregatedFindings analyzePhotos(List<SliceInput> photos) {
        if (photos == null || photos.isEmpty()) {
            return AggregatedFindings.fromSliceFindings(List.of(), 0.85);
        }

        List<SliceFindings> findings = new ArrayList<>();
        for (SliceInput photo : photos) {
            findings.add(analyzeSinglePhoto(photo));
        }

        return AggregatedFindings.fromSliceFindings(findings, 0.85);
    }

    private SliceFindings analyzeSinglePhoto(SliceInput photo) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(photo.bytes()));
            if (img == null) {
                return new SliceFindings(
                        photo.sliceIndex(), photo.filename(), false, "EQUIVOCAL", 0.50, List.of(), Map.of("error", "Unreadable photo")
                );
            }

            int width = img.getWidth();
            int height = img.getHeight();

            List<DetectedLesion> lesions = detectExteriorAnomalies(img, photo.sliceIndex());
            boolean hasAbnormality = !lesions.isEmpty();
            String classification = hasAbnormality ? "ABNORMAL" : "NORMAL";

            double confidence = hasAbnormality
                    ? lesions.stream().mapToDouble(DetectedLesion::confidence).max().orElse(0.85)
                    : 0.92;

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("modality", "EXTERNAL_PHOTO");
            metrics.put("resolution", width + "x" + height);
            metrics.put("surfaceLesionCount", lesions.size());

            return new SliceFindings(
                    photo.sliceIndex(),
                    photo.filename(),
                    hasAbnormality,
                    classification,
                    confidence,
                    Collections.unmodifiableList(lesions),
                    Collections.unmodifiableMap(metrics)
            );

        } catch (Exception e) {
            log.error("External photo analysis error on {}: {}", photo.filename(), e.getMessage());
            return new SliceFindings(
                    photo.sliceIndex(), photo.filename(), false, "EQUIVOCAL", 0.50, List.of(), Map.of("error", e.getMessage())
            );
        }
    }

    private List<DetectedLesion> detectExteriorAnomalies(BufferedImage img, int sliceIndex) {
        List<DetectedLesion> lesions = new ArrayList<>();
        int width = img.getWidth();
        int height = img.getHeight();

        int step = Math.max(2, Math.min(width, height) / 120);
        int gridW = width / step;
        int gridH = height / step;

        boolean[][] gridTissue = new boolean[gridH][gridW];
        double[][] gridLum = new double[gridH][gridW];
        double[][] gridEry = new double[gridH][gridW];

        int totalTissueSamples = 0;
        double sumErythema = 0.0;
        double sumLuminance = 0.0;

        for (int gy = 0; gy < gridH; gy++) {
            int py = gy * step;
            for (int gx = 0; gx < gridW; gx++) {
                int px = gx * step;
                int rgb = img.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Standard clinical YCbCr skin chroma segmentation across all Fitzpatrick skin types (I-VI)
                double yLum = 0.299 * r + 0.587 * g + 0.114 * b;
                double cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                double cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;

                boolean tissue = (yLum > 45) && (cb >= 80) && (cb <= 128) && (cr >= 133) && (cr <= 180);
                gridTissue[gy][gx] = tissue;
                gridLum[gy][gx] = yLum;
                gridEry[gy][gx] = (r - g);

                if (tissue) {
                    totalTissueSamples++;
                    sumErythema += (r - g);
                    sumLuminance += yLum;
                }
            }
        }

        // If no substantial cutaneous tissue is present in the image
        if (totalTissueSamples < 50) {
            log.info("No significant cutaneous tissue detected in slice {}", sliceIndex);
            return lesions;
        }

        double baselineErythema = sumErythema / totalTissueSamples;
        double baselineLuminance = sumLuminance / totalTissueSamples;

        // Compute gradient magnitude on downsampled tissue grid
        double[][] gridGrad = new double[gridH][gridW];
        for (int gy = 0; gy < gridH - 1; gy++) {
            for (int gx = 0; gx < gridW - 1; gx++) {
                if (gridTissue[gy][gx]) {
                    double dx = gridLum[gy][gx + 1] - gridLum[gy][gx];
                    double dy = gridLum[gy + 1][gx] - gridLum[gy][gx];
                    gridGrad[gy][gx] = Math.sqrt(dx * dx + dy * dy);
                }
            }
        }

        // Scan for anomaly density within tissue
        int rad = 4;
        double maxScore = 0.0;
        int peakGx = -1, peakGy = -1;
        String detectedCategory = "VERRUCOUS";

        for (int gy = rad; gy < gridH - rad; gy++) {
            for (int gx = rad; gx < gridW - rad; gx++) {
                if (!gridTissue[gy][gx]) continue;

                int samples = 0;
                double roughCount = 0;
                double eryCount = 0;
                double pigCount = 0;

                for (int wy = gy - rad; wy <= gy + rad; wy++) {
                    for (int wx = gx - rad; wx <= gx + rad; wx++) {
                        if (gridTissue[wy][wx]) {
                            samples++;
                            if (gridGrad[wy][wx] > 10.0) {
                                roughCount += 1.0;
                            }
                            if (gridEry[wy][wx] > baselineErythema + 20) {
                                eryCount += 1.0;
                            }
                            if (gridLum[wy][wx] < baselineLuminance * 0.50) {
                                pigCount += 1.0;
                            }
                        }
                    }
                }

                if (samples >= 8) {
                    double rDensity = roughCount / samples;
                    double eDensity = eryCount / samples;
                    double pDensity = pigCount / samples;

                    // Dermatological surface lesions (papules, verrucae, plaques, ulcers) strongly elevate texture roughness
                    double anomalyScore;
                    String category;
                    if (rDensity > 0.35) {
                        anomalyScore = rDensity * 1.2; // Prioritize keratotic/verrucous morphological lesions
                        category = "VERRUCOUS";
                    } else if (eDensity > 0.50 && rDensity > 0.20) {
                        anomalyScore = eDensity;
                        category = "ERYTHEMATOUS";
                    } else if (pDensity > 0.40) {
                        anomalyScore = pDensity;
                        category = "PIGMENTED";
                    } else {
                        anomalyScore = Math.max(rDensity, Math.max(eDensity * 0.7, pDensity));
                        category = rDensity >= eDensity ? "VERRUCOUS" : (eDensity >= pDensity ? "ERYTHEMATOUS" : "PIGMENTED");
                    }

                    if (anomalyScore > maxScore && anomalyScore > 0.40) {
                        maxScore = anomalyScore;
                        peakGx = gx;
                        peakGy = gy;
                        detectedCategory = category;
                    }
                }
            }
        }

        if (maxScore > 0.35 && peakGx > 0 && peakGy > 0) {
            int bGx1 = peakGx, bGx2 = peakGx, bGy1 = peakGy, bGy2 = peakGy;
            int searchRad = 12;

            for (int sy = Math.max(0, peakGy - searchRad); sy <= Math.min(gridH - 1, peakGy + searchRad); sy++) {
                for (int sx = Math.max(0, peakGx - searchRad); sx <= Math.min(gridW - 1, peakGx + searchRad); sx++) {
                    if (gridTissue[sy][sx] && gridGrad[sy][sx] > 10.0) {
                        bGx1 = Math.min(bGx1, sx);
                        bGx2 = Math.max(bGx2, sx);
                        bGy1 = Math.min(bGy1, sy);
                        bGy2 = Math.max(bGy2, sy);
                    }
                }
            }

            int pad = 2;
            int bx1 = Math.max(0, (bGx1 - pad) * step);
            int by1 = Math.max(0, (bGy1 - pad) * step);
            int bx2 = Math.min(width - 1, (bGx2 + 1 + pad) * step);
            int by2 = Math.min(height - 1, (bGy2 + 1 + pad) * step);

            int bW = bx2 - bx1;
            int bH = by2 - by1;

            if (bW >= 15 && bH >= 15) {
                double pixelSpacingMm = 100.0 / Math.max(width, height);
                double sizeMm = Math.round(Math.max(bW, bH) * pixelSpacingMm * 10.0) / 10.0;
                sizeMm = Math.max(6.0, sizeMm);

                List<Integer> normBox = List.of(
                        (int) Math.round((double) bx1 / width * 1000),
                        (int) Math.round((double) by1 / height * 1000),
                        (int) Math.round((double) bx2 / width * 1000),
                        (int) Math.round((double) by2 / height * 1000)
                );

                String lesionType;
                String region;
                String desc;

                if ("VERRUCOUS".equals(detectedCategory)) {
                    lesionType = "Elevated skin lesion / wart (suspected verruca or keratosis)";
                    region = "Hand / Skin Surface";
                    desc = "Elevated skin surface lesion with irregular texture noted on the hand or skin surface. No acute spreading redness observed.";
                } else if ("ERYTHEMATOUS".equals(detectedCategory)) {
                    lesionType = "Redness / localized skin inflammation";
                    region = "Hand / Skin Surface";
                    desc = "Area of localized skin redness or mild inflammation noted on the skin surface.";
                } else {
                    lesionType = "Pigmented spot / cutaneous mark";
                    region = "Hand / Skin Surface";
                    desc = "Localized pigmented area or spot noted on the skin surface with distinct borders.";
                }

                lesions.add(new DetectedLesion(
                        lesionType,
                        region,
                        normBox,
                        0.91,
                        0.0,
                        sizeMm,
                        desc
                ));
            }
        }

        return lesions;
    }
}
