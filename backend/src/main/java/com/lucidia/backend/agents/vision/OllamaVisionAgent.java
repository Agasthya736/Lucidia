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
        clinician to review. Respond in this exact format:

        SUMMARY: <one paragraph overall impression>
        OBSERVATIONS: <bullet list of discrete notable features, one per line, prefixed with "-">
        REGION: <rough anatomical location of the primary finding>
        CONFIDENCE: <a number from 0.0 to 1.0 representing your certainty>
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