package com.sme.maintenance.kafka;

import java.util.List;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.votepool.VotePool;

public interface KafkaEventProducer {
    void publishRoomClosed(Room room, List<String> recipientSubs);
    void publishVotePoolClosed(Room room, VotePool pool, List<String> recipientSubs, String topIdeaTitle, long totalVotes);
}
