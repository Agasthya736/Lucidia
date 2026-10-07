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
    You are an empathetic, patient-friendly medical explanation assistant specializing in explainable health insights.
    Your audience is normal everyday users and patients on a consumer mobile app (not medical specialists).

    CRITICAL RULES:
    1. Do NOT use dense technical radiology jargon or raw mechanical measurements (e.g. AVOID phrases like "45 mm thick", HU density numbers, or complex slice indices).
    2. Suggest clearly what condition or disease the scan indicates in plain words (e.g., "Possible early pulmonary nodule / mild localized inflammation", "Suspicious skin mole / benign verruca"). If clean, say "No signs of disease detected".
    3. IMPRESSION: A friendly, easy-to-understand 1-2 sentence summary of what might be the problem.
    4. SEVERITY must be exactly one of: "ROUTINE", "FOLLOW_UP_RECOMMENDED", "URGENT".
    5. PATIENT_FRIENDLY_SUMMARY: Explain clearly in simple everyday terms: What might be the issue, where it is located, and why it matters.
    6. RECOMMENDATIONS: Straightforward next steps (e.g. "Schedule a routine visit with your general doctor or pulmonologist for a physical checkup").
    7. Always include a brief reminder that this is an AI estimate and to consult a licensed physician.

    Respond with ONLY a valid JSON object matching this exact schema:
    {
      "suspectedCondition": "Name of suspected condition or disease",
      "clinicalFindings": [
        {
          "region": "Anatomical region (e.g. Right Lung, Left Cheek, Skin)",
          "status": "NORMAL" | "ABNORMAL" | "EQUIVOCAL",
          "description": "Simple, everyday explanation of what was noticed here without raw numerical thickness jargon",
          "sliceIndices": [1, 2]
        }
      ],
      "impression": "Clear, concise conclusion of what might be the problem",
      "severity": "ROUTINE" | "FOLLOW_UP_RECOMMENDED" | "URGENT",
      "patientFriendlySummary": "Warm, non-alarmist, plain-language explanation for everyday users",
      "recommendations": "Simple, actionable advice on which doctor to consult or what steps to take next"
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

    public String answerQuestion(String reportJson, String question, String customApiKey) {
        String system = """
        You are an empathetic, highly informative healthcare explainer AI for patients.
        A user is asking a question about their scan report.
        
        CRITICAL RULES:
        1. DIRECTLY ANSWER THEIR QUESTION: If they ask "What kind of disease is this?" or "What might I have?", explicitly mention what disease or condition the findings point to (e.g. "Based on the findings, this could suggest pneumonia, a bronchial infection, or in some cases a mild inflammation or nodule").
        2. EXPLAIN SIMPLY: Explain the symptoms and meaning in plain everyday language so a non-medical person can easily understand.
        3. BE SPECIFIC: Mention the specific body area and findings from the report in your answer.
        4. Keep your answer warm, reassuring, clear, and around 2-3 short paragraphs.
        5. At the very end, include a gentle 1-sentence note: "Remember, this is an AI estimate to help you understand your scan, not an official diagnosis—always verify with your doctor."
        """;

        String userPrompt = "PATIENT'S SCAN REPORT:\n" + reportJson + "\n\nUSER'S QUESTION: " + question;

        try {
            if (customApiKey != null && !customApiKey.isBlank()) {
                return callGeminiRest(customApiKey.trim(), userPrompt, system);
            } else if (chatClient != null) {
                return chatClient.prompt()
                        .system(system)
                        .user(userPrompt)
                        .call()
                        .content();
            } else if (defaultApiKey != null && !defaultApiKey.isBlank()) {
                return callGeminiRest(defaultApiKey.trim(), userPrompt, system);
            } else {
                return generateIntelligentFallbackAnswer(reportJson, question);
            }
        } catch (Exception e) {
            log.warn("Gemini Q&A answer failed: {}", e.getMessage());
            return generateIntelligentFallbackAnswer(reportJson, question);
        }
    }

    private String generateIntelligentFallbackAnswer(String reportJson, String question) {
        try {
            JsonNode report = objectMapper.readTree(reportJson);
            String condition = report.path("suspectedCondition").asText(report.path("impression").asText("a focal tissue variation"));
            String patientFriendly = report.path("patientFriendlySummary").asText("");
            String recommendations = report.path("recommendations").asText("Routine checkup with a doctor");
            String severity = report.path("severity").asText("ROUTINE");

            String qLower = question.toLowerCase();

            if (qLower.contains("disease") || qLower.contains("what") || qLower.contains("problem") || qLower.contains("have") || qLower.contains("condition")) {
                return String.format(
                        "Based on your scan, the AI identified signs consistent with %s.\n\n" +
                        "%s\n\n" +
                        "This could range from a localized infection or inflammation (like bronchitis or mild pneumonia) to a benign spot or nodule. " +
                        "Please share this report with your physician so they can correlate it with any symptoms (like cough or fever) and evaluate you in person.",
                        condition, patientFriendly.isEmpty() ? "The scan highlights an area that differs slightly from normal surrounding tissue." : patientFriendly);
            } else if (qLower.contains("urgent") || qLower.contains("serious") || qLower.contains("danger") || qLower.contains("scared")) {
                if ("URGENT".equalsIgnoreCase(severity)) {
                    return "The AI noted a significant finding that warrants prompt attention. You should schedule an appointment with your doctor or specialist as soon as possible for a thorough clinical review.";
                } else if ("FOLLOW_UP_RECOMMENDED".equalsIgnoreCase(severity)) {
                    return "The scan shows an area that should be reviewed or monitored, but it does not appear immediately life-threatening. A standard follow-up with your primary physician in the coming days or weeks is advised.";
                } else {
                    return "The scan appears generally reassuring with no urgent high-risk flags detected. Routine health checks remain recommended.";
                }
            } else if (qLower.contains("ask") || qLower.contains("doctor") || qLower.contains("next")) {
                return "When you see your doctor, consider asking:\n" +
                       "• Does this finding explain any symptoms I am currently having?\n" +
                       "• Do I need any follow-up imaging (like another scan in a few months) to see if it changes?\n" +
                       "• Are there any specific treatments or medications recommended?\n\n" +
                       "Recommendations noted on your report: " + recommendations;
            } else {
                return String.format(
                        "Regarding your question: The scan primarily suggests %s.\n\n" +
                        "%s\n\n" +
                        "Because every patient's situation is unique, discussing this directly with your doctor is the best way to get personalized advice.",
                        condition, patientFriendly);
            }
        } catch (Exception ex) {
            return "Based on your scan report, the findings suggest a possible focal condition or inflammation. " +
                   "Depending on your symptoms, this could be related to an infection (such as pneumonia or a chest cold) or a localized nodule. " +
                   "We recommend discussing these results with your healthcare provider for an accurate personal diagnosis.";
        }
    }

    private String callGeminiRest(String apiKey, String prompt) throws Exception {
        return callGeminiRest(apiKey, prompt, SYSTEM_PROMPT);
    }

    private String callGeminiRest(String apiKey, String prompt, String systemPrompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + defaultModel + ":generateContent?key=" + apiKey;

        String requestBody = objectMapper.writeValueAsString(new GeminiPayload(
                List.of(new Content(List.of(
                        new Part(systemPrompt + "\n\n" + prompt)
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

            String suspectedCondition = root.path("suspectedCondition").asText(impression);
            String patientFriendly = root.path("patientFriendlySummary").asText(impression);

            // Detector confidence is strictly anchored to the detector's output
            double detectorConfidence = findings.overallConfidence();

            return new GroundedReport(
                    regions,
                    impression,
                    severity,
                    detectorConfidence,
                    recommendations,
                    generatedBy,
                    false,
                    suspectedCondition,
                    patientFriendly,
                    "AI SAFETY DISCLAIMER: This analysis is AI-generated for informational guidance only and is NOT a medical diagnosis. Please consult a qualified doctor or healthcare professional for clinical evaluation."
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
