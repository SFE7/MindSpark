import { test, describe, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { NotificationRepository } from '../src/db/notificationRepository.js';
import { NotificationKafkaConsumer } from '../src/kafka/kafkaConsumer.js';

describe('Notification Service Tests', () => {
  let repository;
  let app;
  let kafkaConsumer;

  // Mock test verify function simulating Keycloak JWT verification
  const testUsers = {
    'valid-token-user-a': { sub: 'user-a-uuid', email: 'alice@mindspark.test', realm_access: { roles: ['user'] } },
    'valid-token-user-b': { sub: 'user-b-uuid', email: 'bob@mindspark.test', realm_access: { roles: ['user'] } },
    'valid-token-user-c': { sub: 'user-c-uuid', email: 'charlie@mindspark.test', realm_access: { roles: ['user'] } }
  };

  const testVerify = async (token) => {
    if (testUsers[token]) {
      return testUsers[token];
    }
    throw new Error('Invalid signature or expired token');
  };

  beforeEach(() => {
    repository = new NotificationRepository(':memory:');
    const config = {
      port: 8086,
      dbPath: ':memory:',
      kafkaBootstrapServers: ['localhost:9092'],
      eurekaUrl: 'http://localhost:8761/eureka',
      eurekaAppName: 'NOTIFICATION-SERVICE',
      eurekaHostName: 'notification-service',
      eurekaIpAddr: 'notification-service',
      keycloakJwkSetUri: 'http://localhost:8080/certs',
      keycloakIssuerUri: 'http://localhost:8180/realms/mindspark',
      corsAllowedOrigins: ['http://localhost:4200']
    };

    let kafkaConnected = true;
    let eurekaRegistered = true;

    app = createApp({
      config,
      repository,
      getHealthStatus: () => ({ kafkaConnected, eurekaRegistered }),
      authMiddlewareOverride: null // we will create auth middleware with testVerify
    });

    // Re-create app with testVerify in options
    const { createAuthMiddleware } = requireAuth();
    const testAuthMiddleware = createAuthMiddleware({ testVerify });
    app = createApp({
      config,
      repository,
      getHealthStatus: () => ({ kafkaConnected, eurekaRegistered }),
      authMiddlewareOverride: testAuthMiddleware
    });

    kafkaConsumer = new NotificationKafkaConsumer(config, repository);
  });

  afterEach(() => {
    repository.close();
  });

  function requireAuth() {
    return {
      createAuthMiddleware: (opts) => {
        return async (req, res, next) => {
          const auth = req.headers.authorization;
          if (!auth || !auth.startsWith('Bearer ')) {
            return res.status(401).json({ error: 'UNAUTHORIZED', message: 'Missing Bearer token' });
          }
          const token = auth.substring(7).trim();
          try {
            const payload = await testVerify(token);
            req.user = payload;
            next();
          } catch (e) {
            return res.status(401).json({ error: 'UNAUTHORIZED', message: e.message });
          }
        };
      }
    };
  }

  test('GET /api/notifications/health returns 200 with service details', async () => {
    const res = await request(app).get('/api/notifications/health');
    assert.equal(res.status, 200);
    assert.equal(res.body.status, 'UP');
    assert.equal(res.body.service, 'notification-service');
    assert.equal(res.body.framework, 'express');
    assert.equal(res.body.kafkaConnected, true);
    assert.equal(res.body.eurekaRegistered, true);
  });

  test('GET /api/notifications/openapi.json returns 200 and valid schema', async () => {
    const res = await request(app).get('/api/notifications/openapi.json');
    assert.equal(res.status, 200);
    assert.equal(res.body.openapi, '3.0.3');
    assert.equal(res.body.info.title, 'MindSpark Notification Service API');
  });

  test('Unauthenticated GET /api/notifications returns 401 Unauthorized', async () => {
    const res = await request(app).get('/api/notifications');
    assert.equal(res.status, 401);
    assert.equal(res.body.error, 'UNAUTHORIZED');
  });

  test('Invalid token returns 401 Unauthorized', async () => {
    const res = await request(app)
      .get('/api/notifications')
      .set('Authorization', 'Bearer invalid-token-xyz');
    assert.equal(res.status, 401);
    assert.equal(res.body.error, 'UNAUTHORIZED');
  });

  test('Kafka event ingestion handles multi-recipient events and idempotent deduplication', async () => {
    const event1 = {
      eventId: 'evt-unique-001',
      eventType: 'ROOM_CLOSED',
      occurredAt: new Date().toISOString(),
      recipientUserIds: ['user-a-uuid', 'user-b-uuid'],
      title: 'Sprint Room Closed',
      message: 'Room "Sprint Planning" has ended.',
      actionUrl: '/room/room-uuid-1',
      metadata: { roomId: 'room-uuid-1', roomName: 'Sprint Planning' }
    };

    kafkaConsumer.handleEvent(event1);

    // Verify User A sees notification
    const resA = await request(app)
      .get('/api/notifications')
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(resA.status, 200);
    assert.equal(resA.body.items.length, 1);
    assert.equal(resA.body.unreadCount, 1);
    assert.equal(resA.body.items[0].eventId, 'evt-unique-001');
    assert.equal(resA.body.items[0].eventType, 'ROOM_CLOSED');
    assert.equal(resA.body.items[0].title, 'Sprint Room Closed');
    assert.equal(resA.body.items[0].actionUrl, '/room/room-uuid-1');

    // Verify User B sees notification
    const resB = await request(app)
      .get('/api/notifications')
      .set('Authorization', 'Bearer valid-token-user-b');
    assert.equal(resB.status, 200);
    assert.equal(resB.body.items.length, 1);
    assert.equal(resB.body.unreadCount, 1);

    // Verify User C sees NO notifications
    const resC = await request(app)
      .get('/api/notifications')
      .set('Authorization', 'Bearer valid-token-user-c');
    assert.equal(resC.status, 200);
    assert.equal(resC.body.items.length, 0);
    assert.equal(resC.body.unreadCount, 0);

    // Ingest EXACT same event again -> Idempotent, must not duplicate!
    kafkaConsumer.handleEvent(event1);

    const resA2 = await request(app)
      .get('/api/notifications')
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(resA2.body.items.length, 1, 'Duplicate event must be ignored via UNIQUE(event_id, user_id)');
  });

  test('User isolation and zero IDOR: User A cannot read, mark read, or delete User B notification', async () => {
    // Seed notification for User B
    const notifB = repository.insertNotification({
      eventId: 'evt-b-001',
      userId: 'user-b-uuid',
      eventType: 'SUBSCRIPTION_CHANGED',
      title: 'Bob Subscription',
      message: 'Plan upgraded to TEAM',
      actionUrl: '/billing',
      metadata: { planCode: 'TEAM' }
    });
    assert.ok(notifB);

    // User A attempts to mark read Bob's notification -> 404 NOT_FOUND
    const patchRes = await request(app)
      .patch(`/api/notifications/${notifB.id}/read`)
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(patchRes.status, 404, 'User A should receive 404 attempting to modify User B notification');

    // User A attempts to delete Bob's notification -> 404 NOT_FOUND
    const delRes = await request(app)
      .delete(`/api/notifications/${notifB.id}`)
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(delRes.status, 404, 'User A should receive 404 attempting to delete User B notification');

    // Bob can mark it read -> 200 OK
    const bobPatch = await request(app)
      .patch(`/api/notifications/${notifB.id}/read`)
      .set('Authorization', 'Bearer valid-token-user-b');
    assert.equal(bobPatch.status, 200);
    assert.equal(bobPatch.body.isRead, true);

    // Bob can delete it -> 204 No Content
    const bobDel = await request(app)
      .delete(`/api/notifications/${notifB.id}`)
      .set('Authorization', 'Bearer valid-token-user-b');
    assert.equal(bobDel.status, 204);
  });

  test('PATCH /api/notifications/read-all marks all unread read for caller', async () => {
    repository.insertNotification({
      eventId: 'e1',
      userId: 'user-a-uuid',
      eventType: 'VOTE_POOL_CLOSED',
      title: 'Poll 1',
      message: 'Done'
    });
    repository.insertNotification({
      eventId: 'e2',
      userId: 'user-a-uuid',
      eventType: 'VOTE_POOL_CLOSED',
      title: 'Poll 2',
      message: 'Done'
    });

    const countBefore = await request(app)
      .get('/api/notifications/unread-count')
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(countBefore.body.unreadCount, 2);

    const patchAll = await request(app)
      .patch('/api/notifications/read-all')
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(patchAll.status, 200);
    assert.equal(patchAll.body.updatedCount, 2);

    const countAfter = await request(app)
      .get('/api/notifications/unread-count')
      .set('Authorization', 'Bearer valid-token-user-a');
    assert.equal(countAfter.body.unreadCount, 0);
  });

  test('GET /metrics returns 200 with Prometheus metrics including http_requests_total', async () => {
    // Generate a request first
    await request(app).get('/api/notifications/health');

    const res = await request(app).get('/metrics');
    assert.equal(res.status, 200);
    assert.match(res.headers['content-type'], /text\/plain/);
    assert.match(res.text, /http_requests_total/);
  });
});
