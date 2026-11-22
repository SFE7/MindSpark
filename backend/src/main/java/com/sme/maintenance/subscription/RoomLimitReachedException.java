package com.sme.maintenance.subscription;

import lombok.Getter;

@Getter
public class RoomLimitReachedException extends RuntimeException {

    private final String planCode;
    private final int limit;
    private final int current;

    public RoomLimitReachedException(String planCode, int limit, int current) {
        super(String.format("You have reached your active room limit for plan %s (%d/%d). Close or delete an existing room, or upgrade your plan.", planCode, current, limit));
        this.planCode = planCode;
        this.limit = limit;
        this.current = current;
    }
}
