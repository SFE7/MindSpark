package com.sme.maintenance.votepool;

public class VoteBudgetExceededException extends RuntimeException {

    private final int remaining;
    private final int limit;

    public VoteBudgetExceededException(int remaining, int limit) {
        super("VOTE_BUDGET_EXCEEDED");
        this.remaining = remaining;
        this.limit = limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getLimit() {
        return limit;
    }
}
