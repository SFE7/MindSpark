import { Kafka } from 'kafkajs';

export class NotificationKafkaConsumer {
  constructor(config, repository) {
    this.config = config;
    this.repository = repository;
    this.connected = false;
    this.consumer = null;
    this.retryTimer = null;
    this.topics = [
      'mindspark.subscription.changed',
      'mindspark.room.closed',
      'mindspark.vote-pool.closed'
    ];
  }

  async start() {
    try {
      const kafka = new Kafka({
        clientId: 'notification-service',
        brokers: this.config.kafkaBootstrapServers,
        retry: {
          initialRetryTime: 1000,
          retries: 5
        }
      });

      this.consumer = kafka.consumer({ groupId: 'notification-service-group' });

      await this.consumer.connect();
      this.connected = true;
      console.log('[Kafka] Consumer connected to broker(s):', this.config.kafkaBootstrapServers);

      for (const topic of this.topics) {
        await this.consumer.subscribe({ topic, fromBeginning: false });
      }

      await this.consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
          try {
            const raw = message.value.toString('utf-8');
            const event = JSON.parse(raw);
            this.handleEvent(event);
          } catch (err) {
            console.warn(`[Kafka] Error processing message from ${topic}:`, err.message);
          }
        }
      });

      console.log('[Kafka] Subscribed and listening on topics:', this.topics);
    } catch (err) {
      this.connected = false;
      console.warn('[Kafka] Initial connection failed:', err.message, '- scheduling background retry in 10s');
      this.scheduleRetry();
    }
  }

  scheduleRetry() {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(async () => {
      this.retryTimer = null;
      try {
        await this.start();
      } catch (err) {
        this.scheduleRetry();
      }
    }, 10000);
  }

  handleEvent(event) {
    if (!event || !event.eventId || !Array.isArray(event.recipientUserIds)) {
      return;
    }

    const {
      eventId,
      eventType = 'NOTIFICATION',
      title = 'Notification',
      message = '',
      actionUrl = null,
      metadata = {},
      occurredAt = new Date().toISOString()
    } = event;

    for (const userId of event.recipientUserIds) {
      if (!userId || typeof userId !== 'string' || userId.trim() === '') {
        continue;
      }
      this.repository.insertNotification({
        eventId,
        userId: userId.trim(),
        eventType,
        title,
        message,
        actionUrl,
        metadata,
        createdAt: occurredAt
      });
    }
  }

  isConnected() {
    return this.connected;
  }

  async stop() {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    if (this.consumer) {
      try {
        await this.consumer.disconnect();
      } catch (e) {}
      this.connected = false;
    }
  }
}
