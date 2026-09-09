package com.lucidia.backend.quota;

public class QuotaExceededException extends RuntimeException {
    private final int monthlyLimit;
    private final int used;

    public QuotaExceededException(String message, int monthlyLimit, int used) {
        super(message);
        this.monthlyLimit = monthlyLimit;
        this.used = used;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public int getUsed() {
        return used;
    }
}
