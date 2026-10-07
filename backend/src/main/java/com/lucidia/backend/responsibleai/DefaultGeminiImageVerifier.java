package com.lucidia.backend.responsibleai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Production implementation of GeminiImageVerifier that calls Google Gemini
 * via REST with base64 inline image data.
 */
@Component
public class DefaultGeminiImageVerifier implements GeminiImageVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGeminiImageVerifier.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.google.genai.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${spring.ai.google.genai.chat.options.model:${GEMINI_MODEL:gemini-2.5-flash}}")
    private String model;

    @Override
    public ValidationResult verify(byte[] imageBytes, String mimeType, String modality) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key is not configured for image verification gate");
            return ValidationResult.invalid("Could not verify the image, please try again");
        }

        String question;
        if ("EXTERNAL_PHOTO".equalsIgnoreCase(modality)) {
            question = "Does this image clearly show human skin as the main subject? Respond ONLY with JSON {\"valid\": true|false, \"reason\": \"...\"}";
        } else {
            question = "Is this a CT scan slice of the body? Respond ONLY with JSON {\"valid\": true|false, \"reason\": \"...\"}";
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            String effectiveMime = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/jpeg";

            Map<String, Object> requestMap = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", question),
                                    Map.of("inlineData", Map.of(
                                            "mimeType", effectiveMime,
                                            "data", base64Data
                                    ))
                            ))
                    )
            );

            String requestBody = objectMapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini gate call failed with HTTP status {}: {}", response.statusCode(), response.body());
                return ValidationResult.invalid("Could not verify the image, please try again");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidate = root.path("candidates").path(0);
            String text = candidate.path("content").path("parts").path(0).path("text").asText();
            if (text == null || text.isBlank()) {
                log.warn("Gemini gate returned empty candidate content");
                return ValidationResult.invalid("Could not verify the image, please try again");
            }

            String cleaned = text.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }

            JsonNode resultNode = objectMapper.readTree(cleaned);
            boolean valid = resultNode.path("valid").asBoolean(false);
            String reason = resultNode.path("reason").asText(
                    valid ? null : "The uploaded image does not match the required clinical criteria."
            );

            log.info("Gemini gate validation for modality {}: valid={}, reason={}", modality, valid, reason);
            return new ValidationResult(valid, reason);

        } catch (Exception e) {
            log.error("Gemini gate call encountered exception: {}", e.getMessage(), e);
            return ValidationResult.invalid("Could not verify the image, please try again");
        }
    }
}
