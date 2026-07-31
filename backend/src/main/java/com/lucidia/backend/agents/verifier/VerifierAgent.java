package com.lucidia.backend.agents.verifier;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.agents.RetryHelper;
import com.lucidia.backend.agents.vision.MedSamFindings;
import com.lucidia.backend.agents.vision.VisionFindings;
import com.lucidia.backend.agents.writing.ReportDraft;

@Service
public class VerifierAgent {

    private static final String SYSTEM_PROMPT = """
        You are a strict fact-checking assistant for radiology report drafts.
        You will be given one or two independent AI readings of a CT scan,
        optionally some automated segmentation data, and a drafted report
        written from those readings.

        Your job has two parts:

        1. UNSUPPORTED CLAIMS: Identify any sentence in the draft's FINDINGS
        that asserts something (an anatomical structure, finding, location,
        or description) that is NOT stated or reasonably implied by the
        supplied readings or segmentation data. Do not flag sentences that
        merely summarize, compare, or synthesize the readings in different
        words - only flag sentences that introduce new clinical content not
        present in any source.

        2. MISSING FINDINGS: Identify any significant finding, observation,
        or region mentioned in the readings that is absent from the draft's
        FINDINGS section entirely.

        Respond with ONLY valid JSON, no other text, in this exact shape:
        {
          "unsupported_claims": ["<exact sentence from the draft>", ...],
          "missing_findings": ["<short description of what was omitted>", ...]
        }

        If there are none, use empty arrays. Do not include any explanation
        outside the JSON.
        """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerifierAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public VerificationResult verify(ReportDraft draft, VisionFindings a, VisionFindings b, MedSamFindings medSam) {
        String userPrompt = buildPrompt(draft, a, b, medSam);

        String response;
        try {
            response = RetryHelper.callWithFallback(
                    List.of("default"),
                    ignored -> callModel(userPrompt),
                    3,
                    1000
            );
        } catch (Exception e) {
            return VerificationResult.unavailable("Verifier LLM call failed: " + e.getMessage());
        }

        return parseResponse(response);
    }

    private String callModel(String userPrompt) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }

    private String buildPrompt(ReportDraft draft, VisionFindings a, VisionFindings b, MedSamFindings medSam) {
        StringBuilder sb = new StringBuilder();
        if (a != null) {
            sb.append("Reading A (").append(a.provider()).append("): ").append(a.summary()).append("\n");
            sb.append("Observations A: ").append(String.join("; ", a.observations())).append("\n\n");
        }
        if (b != null) {
            sb.append("Reading B (").append(b.provider()).append("): ").append(b.summary()).append("\n");
            sb.append("Observations B: ").append(String.join("; ", b.observations())).append("\n\n");
        }
        if (medSam != null) {
            sb.append(medSam.toPromptContext()).append("\n\n");
        }
        sb.append("Draft FINDINGS:\n").append(draft.findings()).append("\n\n");
        sb.append("Draft IMPRESSION:\n").append(draft.impression());
        return sb.toString();
    }

    private VerificationResult parseResponse(String response) {
        try {
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            JsonNode node = objectMapper.readTree(cleaned);

            List<String> flags = new ArrayList<>();
            JsonNode unsupported = node.get("unsupported_claims");
            if (unsupported != null) {
                for (JsonNode claim : unsupported) {
                    flags.add("Unsupported claim: \"" + claim.asText() + "\"");
                }
            }
            JsonNode missing = node.get("missing_findings");
            if (missing != null) {
                for (JsonNode m : missing) {
                    flags.add("Possibly missing finding: " + m.asText());
                }
            }

            boolean verified = flags.isEmpty();
            String notes = verified
                    ? "All findings traceable to the supplied readings; no significant omissions detected."
                    : flags.size() + " issue(s) found - see flags below.";

            return VerificationResult.of(verified, flags, notes);
        } catch (Exception e) {
            return VerificationResult.unavailable("Failed to parse verifier response: " + e.getMessage());
        }
    }
}