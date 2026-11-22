import './tracing.js';
import { config } from './config.js';
import { NotificationRepository } from './db/notificationRepository.js';
import { NotificationKafkaConsumer } from './kafka/kafkaConsumer.js';
import { EurekaClient } from './eureka/eurekaClient.js';
import { createApp } from './app.js';

const repository = new NotificationRepository(config.dbPath);
const kafkaConsumer = new NotificationKafkaConsumer(config, repository);
const eurekaClient = new EurekaClient(config);

const getHealthStatus = () => ({
  kafkaConnected: kafkaConsumer.isConnected(),
  eurekaRegistered: eurekaClient.isRegistered()
});

const app = createApp({
  config,
  repository,
  getHealthStatus
});

const server = app.listen(config.port, async () => {
  console.log(`[Notification Service] Running on port ${config.port} (Express.js)`);
  console.log(`[Notification Service] SQLite persistence at: ${config.dbPath}`);

  // Non-blocking Kafka consumer start
  kafkaConsumer.start().catch(err => {
    console.warn('[Notification Service] Kafka consumer background start warning:', err.message);
  });

  // Non-blocking Eureka registration start
  eurekaClient.register().catch(err => {
    console.warn('[Notification Service] Eureka registration background start warning:', err.message);
  });
});

async function shutdown() {
  console.log('[Notification Service] Gracefully shutting down...');
  server.close(() => {
    console.log('[Notification Service] HTTP server closed');
  });

  await eurekaClient.deregister();
  await kafkaConsumer.stop();
  repository.close();
  process.exit(0);
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
