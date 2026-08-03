package com.lucidia.backend.agents.writing;

public record ReportDraft(
        String findings,
        String impression,
        String differential,
        boolean flaggedForReview // true if the arbiter found disagreement - surfaced here too
) {}