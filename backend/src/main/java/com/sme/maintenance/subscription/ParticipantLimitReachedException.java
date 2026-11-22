package com.sme.maintenance.subscription;

import lombok.Getter;

@Getter
public class ParticipantLimitReachedException extends RuntimeException {

    private final int limit;
    private final int current;

    public ParticipantLimitReachedException(int limit, int current) {
        super(String.format("This room has reached its host's participant limit (%d/%d).", current, limit));
        this.limit = limit;
        this.current = current;
    }
}
