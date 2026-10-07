package com.lucidia.backend.responsibleai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lucidia.backend.triage.SliceInput;

@Service
public class ResponsibleAiGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(ResponsibleAiGuardrailService.class);

    private static final long MAX_IMAGE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final int MIN_DIMENSION = 64;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/dicom", "image/dicom", "application/octet-stream"
    );

    private final GeminiImageVerifier geminiImageVerifier;

    public ResponsibleAiGuardrailService(@Autowired(required = false) GeminiImageVerifier geminiImageVerifier) {
        this.geminiImageVerifier = geminiImageVerifier;
    }

    /**
     * Enforce strict input validation and server-side Gemini modality gate screening.
     * Order of execution:
     * 1. File checks: allowed types only, max size, minimum dimensions, reject corrupt files. Return HTTP 422.
     * 2. Gemini gate (server-side), one question per modality:
     *    - CT_SERIES: "Is this a CT scan slice of the body? Respond ONLY with JSON {\"valid\": true|false, \"reason\": \"...\"}"
     *    - EXTERNAL_PHOTO: "Does this image clearly show human skin as the main subject? Respond ONLY with JSON {\"valid\": true|false, \"reason\": \"...\"}"
     * 3. If Gemini call fails or times out, reject with "Could not verify the image, please try again". Never let an unchecked image through.
     */
    public void validateUpload(List<SliceInput> slices, String modality) {
        if (slices == null || slices.isEmpty()) {
            throw new ResponsibleAiException("No images submitted for clinical analysis.");
        }

        if (slices.size() > 50) {
            throw new ResponsibleAiException("Series exceeds maximum limit of 50 images per submission.");
        }

        String effectiveModality = (modality != null && !modality.isBlank()) ? modality.toUpperCase() : "CT_SERIES";

        for (int i = 0; i < slices.size(); i++) {
            SliceInput slice = slices.get(i);
            byte[] bytes = slice.bytes();

            if (bytes == null || bytes.length == 0) {
                throw new ResponsibleAiException("Image slice " + (i + 1) + " is empty.");
            }

            if (bytes.length > MAX_IMAGE_SIZE_BYTES) {
                throw new ResponsibleAiException("Image slice " + (i + 1) + " exceeds maximum allowed file size of 25MB.");
            }

            // Allowed MIME type check
            String mime = slice.mimeType();
            if (mime != null && !mime.isBlank() && !ALLOWED_MIME_TYPES.contains(mime.toLowerCase())) {
                throw new ResponsibleAiException("Unsupported file type. Please upload a JPEG, PNG, or WebP image.");
            }

            // Filename extension check
            String filename = slice.filename();
            if (filename != null && !filename.isBlank()) {
                String lower = filename.toLowerCase();
                boolean validExt = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                        || lower.endsWith(".webp") || lower.endsWith(".dcm") || lower.endsWith(".bin");
                if (!validExt) {
                    throw new ResponsibleAiException("Unsupported file format in " + filename + ". Allowed formats: JPEG, PNG, WebP.");
                }
            }

            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                log.warn("Failed to parse image bytes for slice {}: {}", i, e.getMessage());
                throw new ResponsibleAiException("Image slice " + (i + 1) + " contains corrupt or invalid image data.");
            }

            if (img == null) {
                throw new ResponsibleAiException("File is corrupt or not a recognized image format. Please upload JPEG, PNG, or WebP.");
            }

            int width = img.getWidth();
            int height = img.getHeight();

            if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
                throw new ResponsibleAiException("Image resolution " + width + "x" + height +
                        " is below minimum diagnostic threshold of " + MIN_DIMENSION + "x" + MIN_DIMENSION + ".");
            }

            // Screen content heuristic checks
            if ("EXTERNAL_PHOTO".equalsIgnoreCase(effectiveModality)) {
                screenExternalClinicalPhoto(img, i);
            } else {
                screenRadiologyScan(img, i);
            }

            // 2 & 3: Gemini gate verification
            if (geminiImageVerifier == null) {
                throw new ResponsibleAiException("Could not verify the image, please try again");
            }

            GeminiImageVerifier.ValidationResult gateResult = geminiImageVerifier.verify(bytes, mime, effectiveModality);
            if (gateResult == null || !gateResult.isValid()) {
                String reason = (gateResult != null && gateResult.reason() != null && !gateResult.reason().isBlank())
                        ? gateResult.reason()
                        : "Could not verify the image, please try again";
                throw new ResponsibleAiException(reason);
            }
        }
    }

    private void screenExternalClinicalPhoto(BufferedImage img, int sliceIndex) {
        int width = img.getWidth();
        int height = img.getHeight();

        int sampleStep = Math.max(1, Math.min(width, height) / 80);
        int totalSampled = 0;
        int biologicalSkinOrTissuePixels = 0;
        int pureMonochromePixels = 0;
        int plantOrNaturePixels = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                totalSampled++;

                // Check for pure monochrome / artificial flat blocks
                if ((r == 0 && g == 0 && b == 0) || (r >= 250 && g >= 250 && b >= 250)) {
                    pureMonochromePixels++;
                }

                // Check for typical foliage, plant, leaf, tree tones
                if (g > 60 && g > (r * 1.15) && g > (b * 1.15)) {
                    plantOrNaturePixels++;
                }

                // Typical cutaneous tone in RGB
                boolean isSkinTone = (r > 60 && g > 40 && b > 20) &&
                        (r > g) &&
                        ((r - g) >= 10 || (r - b) >= 12) &&
                        (Math.abs(r - g) > 8);

                boolean isErythematousTissue = (r > 100 && r > (g * 1.15) && r > (b * 1.15));

                if (isSkinTone || isErythematousTissue) {
                    biologicalSkinOrTissuePixels++;
                }
            }
        }

        if (totalSampled == 0) return;

        double flatRatio = (double) pureMonochromePixels / totalSampled;
        if (flatRatio > 0.96) {
            throw new ResponsibleAiException("Please upload a valid medical CT slice, X-ray, or clear clinical photograph of the affected area.");
        }

        double plantRatio = (double) plantOrNaturePixels / totalSampled;
        if (plantRatio > 0.20) {
            throw new ResponsibleAiException(
                    "Non-medical image detected (such as foliage or outdoor nature). Please upload a valid medical CT slice, X-ray, or clinical skin photograph to proceed."
            );
        }

        double biologicalRatio = (double) biologicalSkinOrTissuePixels / totalSampled;
        log.info("External photo #{}: biological tissue ratio = {}, plant ratio = {}",
                sliceIndex + 1,
                Math.round(biologicalRatio * 1000.0) / 1000.0,
                Math.round(plantRatio * 1000.0) / 1000.0);

        if (biologicalRatio < 0.015) {
            throw new ResponsibleAiException(
                    "Non-medical image detected. Lucidia cannot process random objects, landscapes, or general photos. Please upload a clear medical scan (CT / X-ray) or clinical photograph of the affected skin/body area."
            );
        }
    }

    private void screenRadiologyScan(BufferedImage img, int sliceIndex) {
        int width = img.getWidth();
        int height = img.getHeight();

        int sampleStep = Math.max(1, Math.min(width, height) / 60);
        int totalSampled = 0;
        int nonZeroPixels = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                totalSampled++;
                if (r > 5 || g > 5 || b > 5) {
                    nonZeroPixels++;
                }
            }
        }

        if (totalSampled > 0 && ((double) nonZeroPixels / totalSampled) < 0.02) {
            throw new ResponsibleAiException("Responsible AI Guardrail: CT scan slice " + (sliceIndex + 1) + " appears blank. Please verify the scan series.");
        }
    }

    public String getResponsibleAiDisclaimer(String modality) {
        if ("EXTERNAL_PHOTO".equalsIgnoreCase(modality)) {
            return "EDUCATIONAL USE NOTICE: External photograph analysis is an informational and observational aid only. It is NOT a diagnostic tool. Always seek in-person evaluation from a qualified healthcare professional for any skin or clinical concerns.";
        }
        return "EDUCATIONAL USE NOTICE: Lucidia provides AI-assisted image analysis for informational purposes only. Results are not a substitute for professional medical advice, diagnosis, or treatment. Consult a qualified healthcare professional with any health concerns.";
    }
}
