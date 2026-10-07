package com.lucidia.backend.responsibleai;

/**
 * Server-side gate verifying that an uploaded image meets clinical requirements
 * for the requested modality using Gemini multimodal vision.
 */
public interface GeminiImageVerifier {

    ValidationResult verify(byte[] imageBytes, String mimeType, String modality);

    record ValidationResult(boolean isValid, String reason) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
