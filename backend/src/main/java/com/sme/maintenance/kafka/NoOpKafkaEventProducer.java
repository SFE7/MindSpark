package com.sme.maintenance.kafka;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.sme.maintenance.room.Room;
import com.sme.maintenance.votepool.VotePool;

@Service
@ConditionalOnProperty(name = "mindspark.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpKafkaEventProducer implements KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(NoOpKafkaEventProducer.class);

    @Override
    public void publishRoomClosed(Room room, List<String> recipientSubs) {
        log.debug("Kafka disabled - skipping room.closed event for room {}", room != null ? room.getId() : null);
    }

    @Override
    public void publishVotePoolClosed(Room room, VotePool pool, List<String> recipientSubs, String topIdeaTitle, long totalVotes) {
        log.debug("Kafka disabled - skipping vote-pool.closed event for pool {}", pool != null ? pool.getId() : null);
    }
}
