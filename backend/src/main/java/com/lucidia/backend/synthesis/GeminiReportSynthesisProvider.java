package com.lucidia.backend.synthesis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

@Component
public class GeminiReportSynthesisProvider implements ReportSynthesisProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiReportSynthesisProvider.class);

    private static final String SYSTEM_PROMPT = """
    You are an expert radiology reporting assistant specializing in evidence-grounded synthesis.
    You write clear, objective, and structured radiology reports synthesized STRICTLY from the
    provided objective CT triage detector findings.

    CRITICAL RULES:
    1. Do NOT invent or hallucinate lesions, anatomic structures, or measurements not present in the detector evidence.
    2. IMPRESSION must be an explicit, unambiguous statement of what appears wrong (e.g., "Suspected 8mm pulmonary nodule, right upper lobe") or "No significant abnormality detected" if clean.
    3. SEVERITY must be exactly one of: "ROUTINE", "FOLLOW_UP_RECOMMENDED", "URGENT".
    4. Provide region-by-region clinical findings. Detail abnormal findings thoroughly; keep normal findings concise.
    5. Provide evidence-based clinical recommendations (e.g. follow-up imaging, correlate clinically, no action needed).

    Respond with ONLY a valid JSON object matching this exact schema:
    {
      "clinicalFindings": [
        {
          "region": "Anatomical region name",
          "status": "NORMAL" | "ABNORMAL" | "EQUIVOCAL",
          "description": "Objective descriptive findings",
          "sliceIndices": [1, 2]
        }
      ],
      "impression": "Explicit summary statement",
      "severity": "ROUTINE" | "FOLLOW_UP_RECOMMENDED" | "URGENT",
      "recommendations": "Actionable next steps"
    }
    """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${spring.ai.google.genai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}")
    private String defaultModel;

    public GeminiReportSynthesisProvider(@Autowired(required = false) ChatClient.Builder chatClientBuilder) {
        if (chatClientBuilder != null) {
            this.chatClient = chatClientBuilder.build();
        } else {
            this.chatClient = null;
        }
    }

    @Override
    public String getProviderId() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return (defaultApiKey != null && !defaultApiKey.isBlank()) || chatClient != null;
    }

    @Override
    public GroundedReport synthesize(AggregatedFindings findings, String customApiKey) {
        String prompt = buildUserPrompt(findings);

        String jsonResponse;
        String modelUsed = "gemini-2.5-flash";

        try {
            if (customApiKey != null && !customApiKey.isBlank()) {
                log.info("Performing grounded synthesis via BYOK Gemini key");
                jsonResponse = callGeminiRest(customApiKey.trim(), prompt);
                modelUsed = "gemini-byok";
            } else if (chatClient != null) {
                log.info("Performing grounded synthesis via default ChatClient");
                jsonResponse = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(prompt)
                        .call()
                        .content();
            } else if (defaultApiKey != null && !defaultApiKey.isBlank()) {
                log.info("Performing grounded synthesis via default API key REST");
                jsonResponse = callGeminiRest(defaultApiKey.trim(), prompt);
            } else {
                throw new IllegalStateException("No Gemini API key or ChatClient available.");
            }

            return parseJsonToReport(jsonResponse, findings, "Gemini Grounded Synthesis (" + modelUsed + ")");

        } catch (Exception e) {
            log.error("Gemini synthesis failed: {}", e.getMessage());
            throw new RuntimeException("Grounded synthesis with Gemini failed: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(AggregatedFindings findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("AGGREGATED TRIAGE DETECTOR EVIDENCE:\n");
        sb.append("- Total Slices: ").append(findings.totalSlices()).append("\n");
        sb.append("- Abnormal Slices: ").append(findings.abnormalSlicesCount()).append("\n");
        sb.append("- Detector Overall Status: ").append(findings.overallStatus()).append("\n");
        sb.append("- Detector Confidence: ").append(findings.overallConfidence()).append("\n");
        sb.append("- Evidence Summary: ").append(findings.summaryEvidence()).append("\n\n");

        sb.append("DETECTED FOCAL LESIONS:\n");
        if (findings.topLesions().isEmpty()) {
            sb.append("None detected across visualized slices.\n");
        } else {
            for (DetectedLesion l : findings.topLesions()) {
                sb.append(String.format("• [%s] %s: BoundingBox=%s, Confidence=%.2f, EstHU=%.1f, EstSize=%.1fmm. Notes: %s\n",
                        l.anatomicalRegion(), l.lesionType(), l.boundingBox(), l.confidence(), l.densityHu(), l.sizeMm(), l.description()));
            }
        }

        sb.append("\nPlease synthesize the structured JSON radiology report strictly matching the schema.");
        return sb.toString();
    }

    private String callGeminiRest(String apiKey, String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + defaultModel + ":generateContent?key=" + apiKey;

        String requestBody = objectMapper.writeValueAsString(new GeminiPayload(
                List.of(new Content(List.of(
                        new Part(SYSTEM_PROMPT + "\n\n" + prompt)
                )))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidate = root.path("candidates").get(0);
        return candidate.path("content").path("parts").get(0).path("text").asText();
    }

    private GroundedReport parseJsonToReport(String rawJson, AggregatedFindings findings, String generatedBy) {
        try {
            String cleaned = rawJson.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }

            JsonNode root = objectMapper.readTree(cleaned);

            List<RegionalFinding> regions = new ArrayList<>();
            JsonNode findingsNode = root.path("clinicalFindings");
            if (findingsNode.isArray()) {
                for (JsonNode f : findingsNode) {
                    List<Integer> slices = new ArrayList<>();
                    JsonNode sliceArray = f.path("sliceIndices");
                    if (sliceArray.isArray()) {
                        for (JsonNode s : sliceArray) slices.add(s.asInt());
                    }
                    regions.add(new RegionalFinding(
                            f.path("region").asText("Thorax"),
                            f.path("status").asText("NORMAL").toUpperCase(),
                            f.path("description").asText(""),
                            slices
                    ));
                }
            }

            String impression = root.path("impression").asText(
                    findings.abnormalSlicesCount() > 0 ? "Suspected focal abnormality detected on CT series." : "No significant abnormality detected."
            );

            String severity = root.path("severity").asText(
                    findings.abnormalSlicesCount() > 0 ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE"
            ).toUpperCase();

            String recommendations = root.path("recommendations").asText("Clinical correlation recommended.");

            // Detector confidence is strictly anchored to the detector's output
            double detectorConfidence = findings.overallConfidence();

            return new GroundedReport(
                    regions,
                    impression,
                    severity,
                    detectorConfidence,
                    recommendations,
                    generatedBy,
                    false
            );

        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON output ({}). Using fallback parser.", e.getMessage());
            return FallbackReportSynthesisProvider.createGroundedFromDetector(findings);
        }
    }

    private record GeminiPayload(List<Content> contents) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}
