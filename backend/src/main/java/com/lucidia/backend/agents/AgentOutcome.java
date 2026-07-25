package com.lucidia.backend.agents;

public record AgentOutcome<T>(boolean success, T value, String errorMessage) {
    public static <T> AgentOutcome<T> ok(T value) {
        return new AgentOutcome<>(true, value, null);
    }
    public static <T> AgentOutcome<T> failed(String message) {
        return new AgentOutcome<>(false, null, message);
    }
}