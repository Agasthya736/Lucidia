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
    independent AI readings of the same CT scan, and optionally supporting
    quantitative segmentation data. Write a report in this exact format:

    FINDINGS: <objective description synthesizing the reading(s). Reuse the
    specific anatomical terms, structures, and descriptive language each
    reading actually used, rather than paraphrasing into different wording -
    this keeps the report traceable to its source. If two readings are
    given and they conflict, describe both readings using their own terms
    rather than picking one or summarizing the disagreement abstractly.
    If only one reading is available, note that explicitly and describe it
    alone, again using its own wording. If quantitative segmentation data is
    provided, mention it only after the qualitative findings, clearly
    labeled as automated segmentation output - reference it as supporting
    detail, never as a diagnosis, and never as the basis for describing
    anatomy neither reading mentioned.
    Do not open with a meta-summary sentence about the readings themselves
    (e.g. "Two readings were provided and disagree") - begin directly with
    the clinical content.>
    IMPRESSION: <brief summary; if two readings disagree, state clearly that
    findings are discordant and require clinician review, and name the
    specific point of disagreement using language drawn from the readings;
    if only one reading was available, note that no second opinion was
    possible and clinician review is still required>
    DIFFERENTIAL: <Only include this if the findings describe a genuine
    abnormality - a mass, lesion, opacity, effusion, or other discrete
    abnormal finding. If the readings describe a normal or unremarkable
    scan, write exactly: "No abnormality identified; differential not
    applicable." Otherwise, based only on the morphological features
    described above, list 2-4 possible general categories of explanation a
    clinician might consider, from most to least likely given the visual
    description. Use general categories, not definitive disease names (e.g.
    "a malignant neoplasm" rather than a specific cancer subtype, "an
    infectious or inflammatory process" rather than naming a specific
    pathogen). Every entry must be phrased as a possibility, never a
    conclusion (e.g. "could represent...", "is consistent with, among other
    possibilities..."). Do not introduce any finding or feature not already
    stated in FINDINGS.>

    Do not introduce anatomical terms, measurements, or observations that
    do not appear in the supplied reading(s) or segmentation data.
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
        String impression = extract(response, "IMPRESSION:", "DIFFERENTIAL:");
        String differential = extract(response, "DIFFERENTIAL:", null);
        boolean flagged = !arbitration.available() || !arbitration.agree();

        return new ReportDraft(findings.trim(), impression.trim(), differential.trim(), flagged);
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