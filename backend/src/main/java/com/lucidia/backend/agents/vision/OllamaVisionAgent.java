package com.lucidia.backend.agents.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

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
    private final String visionModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaVisionAgent(
            @Value("${lucidia.ollama.base-url}") String baseUrl,
            @Value("${lucidia.ollama.vision-model}") String visionModel) {
        this.restClient = RestClient.create(baseUrl);
        this.visionModel = visionModel;
    }

    @Override
    public VisionFindings analyze(byte[] imageBytes, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = Map.of(
                "model", visionModel,
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
            String responseText = node.get("response").asText();
            return VisionFindingsParser.parse(responseText, providerName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }

    @Override
    public String providerName() {
        return visionModel;
    }
}