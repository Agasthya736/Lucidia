package com.lucidia.backend.agents.writing;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.RetryHelper;
import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.vision.MedSamFindings;
import com.lucidia.backend.agents.vision.VisionFindings;

@Service
public class WritingAgent {

    private static final String SYSTEM_PROMPT = """
        You are a radiology report writing assistant. You draft documentation
        only - you do not diagnose. You will be given either one or two
        independent AI readings of the same CT scan, and optionally
        supporting quantitative segmentation data. Write a report in this
        exact format:

        FINDINGS: <objective description. If two readings are given and they
        conflict, describe both readings rather than picking one. If only one
        reading is available, note that explicitly and describe it alone.
        If quantitative segmentation data is provided, you may reference it
        as supporting detail, but do not treat it as a diagnosis.>
        IMPRESSION: <brief summary; if two readings disagree, state clearly
        that findings are discordant and require clinician review; if only
        one reading was available, note that no second opinion was possible
        and clinician review is still required>
        """;

    private final ChatClient chatClient;

    public WritingAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ReportDraft draft(VisionFindings a, VisionFindings b, ArbitrationResult arbitration,
                              MedSamFindings medSam) {
        String userPrompt = (a != null && b != null)
                ? buildDualPrompt(a, b, arbitration, medSam)
                : buildSinglePrompt(a != null ? a : b, medSam);

        String response = RetryHelper.callWithFallback(
                List.of("default"),
                ignored -> callModel(userPrompt),
                3,
                1000
        );

        String findings = extract(response, "FINDINGS:", "IMPRESSION:");
        String impression = extract(response, "IMPRESSION:", null);
        boolean flagged = !arbitration.available() || !arbitration.agree();

        return new ReportDraft(findings.trim(), impression.trim(), flagged);
    }

    private String callModel(String userPrompt) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }

    private String buildDualPrompt(VisionFindings a, VisionFindings b, ArbitrationResult arbitration,
                                    MedSamFindings medSam) {
        return String.format("""
            Reading A (%s): %s
            Observations A: %s

            Reading B (%s): %s
            Observations B: %s

            Agreement: %s (score %.2f)
            Arbitration notes: %s

            %s
            """,
                a.provider(), a.summary(), String.join("; ", a.observations()),
                b.provider(), b.summary(), String.join("; ", b.observations()),
                arbitration.agree() ? "AGREE" : "DISAGREE",
                arbitration.agreementScore(),
                arbitration.notes(),
                medSamContext(medSam)
        );
    }

    private String buildSinglePrompt(VisionFindings only, MedSamFindings medSam) {
        return String.format("""
            Only one reading was available (the second vision agent failed).

            Reading (%s): %s
            Observations: %s

            %s
            """,
                only.provider(), only.summary(), String.join("; ", only.observations()),
                medSamContext(medSam)
        );
    }

    private String medSamContext(MedSamFindings medSam) {
        return medSam != null
                ? medSam.toPromptContext()
                : "MedSAM segmentation: not available for this scan.";
    }

    private String extract(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();
        int e = (end != null) ? text.indexOf(end, s) : text.length();
        if (e == -1) e = text.length();
        return text.substring(s, e);
    }
}