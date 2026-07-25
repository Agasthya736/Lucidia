package com.lucidia.backend.agents;

import java.util.List;
import java.util.function.Function;

public class RetryHelper {

    public static <T> T callWithFallback(
            List<String> modelsInPriorityOrder,
            Function<String, T> callFn,
            int maxAttemptsPerModel,
            long initialBackoffMs) {

        StringBuilder failures = new StringBuilder();

        for (String model : modelsInPriorityOrder) {
            long backoff = initialBackoffMs;
            for (int attempt = 1; attempt <= maxAttemptsPerModel; attempt++) {
                try {
                    return callFn.apply(model);
                } catch (Exception e) {
                    failures.append(String.format("[%s attempt %d/%d: %s] ",
                            model, attempt, maxAttemptsPerModel, e.getMessage()));
                    if (attempt < maxAttemptsPerModel) {
                        sleep(backoff);
                        backoff *= 2;
                    }
                }
            }
        }

        throw new RuntimeException("All models and retries exhausted: " + failures);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}