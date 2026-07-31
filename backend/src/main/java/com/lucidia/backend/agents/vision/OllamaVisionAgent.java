package com.lucidia.backend.agents.vision;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.agents.RetryHelper;

@Service
public class OllamaVisionAgent implements VisionAgent {

    private static final String SYSTEM_PROMPT = """
    You are a radiology assistant analyzing a CT scan image for documentation
    purposes only - you do not provide diagnoses, only observations for a
    clinician to review.

    Be precise and cautious:
    - Describe only what is visually present (shape, density, location,
      borders, size relative to surrounding structures). Do not name a
      specific disease or condition (e.g. do not say "adenocarcinoma" or
      "pneumonia") - describe the morphology instead (e.g. "an irregular,
      spiculated hyperdense mass").
    - If you are not confident which anatomical region or organ you are
      looking at, say so explicitly rather than guessing. It is better to
      report uncertainty than to state a wrong anatomical location with
      false confidence.
    - Your CONFIDENCE score must reflect your actual certainty. If the
      image is ambiguous, ill-defined, or you are unsure of the anatomy,
      use a low score (below 0.5). Do not default to a high score.

    Respond in this exact format:

    SUMMARY: <one paragraph overall impression>
    OBSERVATIONS: <bullet list of discrete notable features, one per line, prefixed with "-">
    REGION: <rough anatomical location of the primary finding, or "uncertain" if unclear>
    BBOX: <a tight bounding box around the single most significant finding, as
    four comma-separated integers x1,y1,x2,y2 on a 0-1000 scale relative to
    the image width and height (top-left is 0,0; bottom-right is 1000,1000).
    If there is no single discrete finding to box, or you are not confident
    estimating spatial coordinates, write "unknown" instead.>
    CONFIDENCE: <a number from 0.0 to 1.0 representing your genuine certainty>
    """;

    private final RestClient restClient;
    private final List<String> modelFallbackChain;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaVisionAgent(
            @Value("${lucidia.ollama.base-url}") String baseUrl,
            @Value("${lucidia.ollama.vision-model}") String primaryModel,
            @Value("${lucidia.ollama.vision-model-fallback:}") String fallbackModel) {
        this.restClient = RestClient.create(baseUrl);
        this.modelFallbackChain = fallbackModel.isBlank()
                ? List.of(primaryModel)
                : List.of(primaryModel, fallbackModel);
    }

    @Override
    public VisionFindings analyze(byte[] imageBytes, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String response = RetryHelper.callWithFallback(
                modelFallbackChain,
                model -> callModel(model, base64Image),
                3,
                2000
        );

        return VisionFindingsParser.parse(response, providerName());
    }

    private String callModel(String modelName, String base64Image) {
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "prompt", SYSTEM_PROMPT + "\n\nAnalyze this CT scan.",
                "images", List.of(base64Image),
                "stream", false
        );

        String rawResponse = restClient.post()
                .uri("/api/generate")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(rawResponse);
            return node.get("response").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response: " + e.getMessage());
        }
    }

    @Override
    public String providerName() {
        return modelFallbackChain.get(0);
    }
}