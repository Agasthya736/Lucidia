package com.lucidia.backend.agents.writing;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.vision.VisionFindings;

@Service
public class WritingAgent {

    private static final String SYSTEM_PROMPT = """
        You are a radiology report writing assistant. You draft documentation
        only - you do not diagnose. Given two independent AI readings of the
        same CT scan and a note on whether they agree, write a report in this
        exact format:

        FINDINGS: <objective description synthesizing both readings; if they
        conflict, describe both readings rather than picking one>
        IMPRESSION: <brief summary; if the readings disagree, state clearly
        that findings are discordant and require clinician review>
        """;

    private final ChatClient chatClient;

    public WritingAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ReportDraft draft(VisionFindings a, VisionFindings b, ArbitrationResult arbitration) {
        String userPrompt = String.format("""
            Reading A (%s): %s
            Observations A: %s

            Reading B (%s): %s
            Observations B: %s

            Agreement: %s (score %.2f)
            Arbitration notes: %s
            """,
                a.provider(), a.summary(), String.join("; ", a.observations()),
                b.provider(), b.summary(), String.join("; ", b.observations()),
                arbitration.agree() ? "AGREE" : "DISAGREE",
                arbitration.agreementScore(),
                arbitration.notes()
        );

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        String findings = extract(response, "FINDINGS:", "IMPRESSION:");
        String impression = extract(response, "IMPRESSION:", null);

        return new ReportDraft(findings.trim(), impression.trim(), !arbitration.agree());
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