package com.lucidia.backend.agents.verifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.vision.VisionFindings;
import com.lucidia.backend.agents.writing.ReportDraft;

@Service
public class VerifierAgent {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "is", "are", "of", "in", "on", "at", "no", "and", "or",
            "with", "within", "without", "to", "for", "this", "that", "both", "readings",
            "clinician", "review", "required", "discordant"
    );

    public VerificationResult verify(ReportDraft draft, VisionFindings a, VisionFindings b) {
        Set<String> groundedTokens = new HashSet<>();
        groundedTokens.addAll(tokenize(a.summary()));
        groundedTokens.addAll(tokenize(b.summary()));
        for (String obs : a.observations()) groundedTokens.addAll(tokenize(obs));
        for (String obs : b.observations()) groundedTokens.addAll(tokenize(obs));

        List<String> flags = new ArrayList<>();
        for (String sentence : draft.findings().split("(?<=[.!?])\\s+")) {
            if (sentence.isBlank()) continue;
            Set<String> sentenceTokens = tokenize(sentence);
            long overlap = sentenceTokens.stream().filter(groundedTokens::contains).count();
            double ratio = sentenceTokens.isEmpty() ? 0 : (double) overlap / sentenceTokens.size();
            if (ratio < 0.3) {
                flags.add("Unsupported claim: \"" + sentence.trim() + "\"");
            }
        }

        boolean verified = flags.isEmpty();
        String notes = verified
                ? "All findings traceable to at least one vision agent's observations."
                : flags.size() + " statement(s) in the draft could not be traced to either agent's findings.";

        return new VerificationResult(verified, flags, notes);
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String word : text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+")) {
            if (word.length() > 2 && !STOPWORDS.contains(word)) tokens.add(word);
        }
        return tokens;
    }
}