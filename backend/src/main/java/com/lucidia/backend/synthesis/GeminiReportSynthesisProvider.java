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
    2. Describe findings as plain observations (e.g. "a raised skin area", "a reddened area", "a darker patch", "a focal opacity"). NEVER use words like "normal", "healthy", "benign", or "malignant". NEVER state "you have X" or give a definitive medical diagnosis.
    3. IMPRESSION: A friendly, easy-to-understand 1-2 sentence summary of what was observed.
    4. SEVERITY must be exactly one of: "ROUTINE", "FOLLOW_UP_RECOMMENDED", "URGENT".
    5. PATIENT_FRIENDLY_SUMMARY: Explain clearly in simple everyday terms what was observed, where it is located, and why it is important to consult a doctor.
    6. RECOMMENDATIONS: Straightforward next steps (e.g. "Schedule a visit with a doctor or specialist for a clinical evaluation").
    7. Always include a reminder that the tool is not a medical device and to consult a licensed healthcare professional.

    Respond with ONLY a valid JSON object matching this exact schema:
    {
      "suspectedCondition": "General description of observation",
      "clinicalFindings": [
        {
          "region": "Anatomical region (e.g. Right Lung, Left Cheek, Skin)",
          "status": "FINDINGS_DETECTED" | "NO_FINDINGS_DETECTED" | "INCONCLUSIVE",
          "description": "Simple, everyday observation of what was noticed here without raw numerical thickness jargon",
          "sliceIndices": [1, 2]
        }
      ],
      "impression": "The tool detected features that may need attention. See a doctor.",
      "severity": "ROUTINE" | "FOLLOW_UP_RECOMMENDED" | "URGENT",
      "patientFriendlySummary": "Warm, non-alarmist, plain-language explanation of observations for everyday users",
      "recommendations": "Simple, actionable advice on consulting a doctor or what steps to take next"
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
        You are an empathetic, informative informational health explainer AI.
        A user is asking a question about their scan report.
        
        CRITICAL RULES:
        1. NEVER STATE A DIAGNOSIS: Never say "you have X" or confirm a specific medical condition.
        2. NEVER USE BANNED WORDS: Never use words like "normal", "healthy", "benign", or "malignant".
        3. EXPLAIN OBSERVATIONS: Explain what was observed in plain, everyday language at a grade 6 reading level.
        4. ALWAYS RECOMMEND A DOCTOR: Encourage the user to discuss the observations with a qualified physician for clinical evaluation.
        5. Keep your answer warm, clear, and around 2 short paragraphs.
        6. At the very end, include: "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional."
        """;

        String userPrompt = "USER'S SCAN REPORT:\n" + reportJson + "\n\nUSER'S QUESTION: " + question;

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
            String recommendations = report.path("recommendations").asText("Consult a healthcare professional for an evaluation");
            String severity = report.path("severity").asText("ROUTINE");

            String qLower = question.toLowerCase();

            if (qLower.contains("disease") || qLower.contains("what") || qLower.contains("problem") || qLower.contains("have") || qLower.contains("condition")) {
                return String.format(
                        "Based on your scan, the tool observed: %s.\n\n" +
                        "%s\n\n" +
                        "This is an automated observation, not a medical diagnosis. " +
                        "Please share this report with your physician so they can examine this in person.",
                        condition, patientFriendly.isEmpty() ? "The scan highlights a localized area of variation." : patientFriendly);
            } else if (qLower.contains("urgent") || qLower.contains("serious") || qLower.contains("danger") || qLower.contains("scared")) {
                if ("URGENT".equalsIgnoreCase(severity)) {
                    return "The tool noted a significant feature that may need prompt attention. You should schedule an appointment with a doctor as soon as possible for an in-person evaluation.";
                } else if ("FOLLOW_UP_RECOMMENDED".equalsIgnoreCase(severity)) {
                    return "The tool noted a feature that should be reviewed or monitored by a physician. A follow-up with your primary doctor in the coming weeks is advised.";
                } else {
                    return "The tool did not identify urgent flags, but this is not a medical clearance. If you have symptoms, please consult a healthcare professional.";
                }
            } else if (qLower.contains("ask") || qLower.contains("doctor") || qLower.contains("next")) {
                return "When you see your doctor, consider asking:\n" +
                       "• Does this observation explain any symptoms I am currently having?\n" +
                       "• Do I need any follow-up imaging to monitor this area?\n" +
                       "• What next steps do you recommend?\n\n" +
                       "Recommendations noted on your report: " + recommendations;
            } else {
                return String.format(
                        "Regarding your question: The scan observation indicates %s.\n\n" +
                        "%s\n\n" +
                        "Because every individual's situation is unique, discussing this directly with your doctor is the best way to get personalized guidance.",
                        condition, patientFriendly);
            }
        } catch (Exception ex) {
            return "Based on your scan report, the tool observed a localized area of interest. " +
                   "The tool detected features that may need attention. Please discuss these observations with your healthcare professional for an in-person evaluation.";
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
                            AggregatedFindings.mapToResultBand(f.path("status").asText("NO_FINDINGS_DETECTED")),
                            f.path("description").asText(""),
                            slices
                    ));
                }
            }

            String impression = root.path("impression").asText(
                    findings.bandMessage()
            );

            String severity = root.path("severity").asText(
                    findings.abnormalSlicesCount() > 0 ? "FOLLOW_UP_RECOMMENDED" : "ROUTINE"
            ).toUpperCase();

            String recommendations = root.path("recommendations").asText("Consult a healthcare professional for clinical evaluation.");

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
                    "Not a medical device. Does not diagnose, treat, cure or prevent any condition. Consult a healthcare professional."
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
