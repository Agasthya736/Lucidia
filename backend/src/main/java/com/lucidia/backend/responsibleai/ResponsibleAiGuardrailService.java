package com.lucidia.backend.responsibleai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lucidia.backend.triage.SliceInput;

@Service
public class ResponsibleAiGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(ResponsibleAiGuardrailService.class);

    private static final long MAX_IMAGE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
    private static final int MIN_DIMENSION = 64;

    /**
     * Enforce strict Responsible AI screening on incoming image series.
     * Prevents system abuse (memes, blank files, synthetic non-medical graphics, random screenshots).
     */
    public void validateUpload(List<SliceInput> slices, String modality) {
        if (slices == null || slices.isEmpty()) {
            throw new ResponsibleAiException("Responsible AI Guardrail: No images submitted for clinical analysis.");
        }

        if (slices.size() > 50) {
            throw new ResponsibleAiException("Responsible AI Guardrail: Series exceeds maximum limit of 50 images per submission.");
        }

        for (int i = 0; i < slices.size(); i++) {
            SliceInput slice = slices.get(i);
            byte[] bytes = slice.bytes();

            if (bytes == null || bytes.length == 0) {
                throw new ResponsibleAiException("Responsible AI Guardrail: Image slice " + (i + 1) + " is empty.");
            }

            if (bytes.length > MAX_IMAGE_SIZE_BYTES) {
                throw new ResponsibleAiException("Responsible AI Guardrail: Image slice " + (i + 1) + " exceeds maximum allowed file size of 25MB.");
            }

            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                log.warn("Failed to parse image bytes for slice {}: {}", i, e.getMessage());
                throw new ResponsibleAiException("Responsible AI Guardrail: Image slice " + (i + 1) + " contains corrupt or invalid image data.");
            }

            if (img == null) {
                throw new ResponsibleAiException("Responsible AI Guardrail: Unsupported image format in slice " + (i + 1) + ". Please upload JPEG, PNG, or DICOM.");
            }

            int width = img.getWidth();
            int height = img.getHeight();

            if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
                throw new ResponsibleAiException("Responsible AI Guardrail: Image resolution " + width + "x" + height +
                        " is below minimum diagnostic threshold of " + MIN_DIMENSION + "x" + MIN_DIMENSION + ".");
            }

            // Screen content based on modality
            if ("EXTERNAL_PHOTO".equalsIgnoreCase(modality)) {
                screenExternalClinicalPhoto(img, i);
            } else {
                screenRadiologyScan(img, i);
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

                // Check for pure monochrome / artificial flat blocks (e.g. solid test cards / memes)
                if ((r == 0 && g == 0 && b == 0) || (r >= 250 && g >= 250 && b >= 250)) {
                    pureMonochromePixels++;
                }

                // Check for typical foliage, plant, leaf, tree tones (strong green predominance)
                if (g > 60 && g > (r * 1.15) && g > (b * 1.15)) {
                    plantOrNaturePixels++;
                }

                // Typical cutaneous tone in RGB: R > G > B with moderate contrast, or erythema (R >> G, B)
                boolean isSkinTone = (r > 60 && g > 40 && b > 20) &&
                        (r > g) &&
                        ((r - g) >= 10 || (r - b) >= 12) &&
                        (Math.abs(r - g) > 8);

                // Or inflammatory erythema / mucosal / tissue reflectance:
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

        // If biological tissue ratio is virtually zero (< 1.5%), reject non-clinical / random image
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
            return "RESPONSIBLE AI & CLINICAL SAFETY NOTICE: External clinical photograph analysis provides automated morphological screening support only. It is NOT a histopathological, dermoscopic, or definitive biopsy diagnosis. Direct in-person clinical examination and practitioner sign-off is mandatory before making diagnostic or treatment decisions.";
        }
        return "CLINICAL DECISION SUPPORT NOTICE: Lucidia provides AI-assisted second-read CT documentation. It does NOT provide autonomous diagnostic decisions. Documented clinician review and sign-off is mandatory before clinical action.";
    }
}
