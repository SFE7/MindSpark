package com.mindspark.ai.common.exception;

import lombok.Getter;

@Getter
public class DailyLimitReachedException extends RuntimeException {
    private final int dailyLimit;

    public DailyLimitReachedException(int dailyLimit) {
        super("Daily request limit reached (" + dailyLimit + " requests/day). Premium users get a higher daily limit.");
        this.dailyLimit = dailyLimit;
    }
}
