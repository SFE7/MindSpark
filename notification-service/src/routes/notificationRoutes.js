import { Router } from 'express';
import { openApiSpec } from '../docs/openapi.js';

export function createNotificationRoutes({ repository, authMiddleware, getHealthStatus }) {
  const router = Router();

  // Public Health endpoint
  router.get('/health', (req, res) => {
    const health = getHealthStatus ? getHealthStatus() : {};
    res.json({
      status: 'UP',
      service: 'notification-service',
      framework: 'express',
      kafkaConnected: Boolean(health.kafkaConnected),
      eurekaRegistered: Boolean(health.eurekaRegistered)
    });
  });

  // Public OpenAPI spec JSON
  router.get('/openapi.json', (req, res) => {
    res.json(openApiSpec);
  });

  // Protected: GET / (list notifications)
  router.get('/', authMiddleware, (req, res) => {
    const userId = req.user.sub;
    const limit = parseInt(req.query.limit, 10) || 30;
    const unreadOnly = req.query.unreadOnly === 'true' || req.query.unreadOnly === true;

    const result = repository.listNotifications(userId, { limit, unreadOnly });
    res.json(result);
  });

  // Protected: GET /unread-count
  router.get('/unread-count', authMiddleware, (req, res) => {
    const userId = req.user.sub;
    const unreadCount = repository.countUnread(userId);
    res.json({ unreadCount });
  });

  // Protected: PATCH /read-all
  router.patch('/read-all', authMiddleware, (req, res) => {
    const userId = req.user.sub;
    const result = repository.markAllRead(userId);
    res.json(result);
  });

  // Protected: PATCH /:id/read
  router.patch('/:id/read', authMiddleware, (req, res) => {
    const userId = req.user.sub;
    const { id } = req.params;

    const updated = repository.markAsRead(id, userId);
    if (!updated) {
      return res.status(404).json({
        error: 'NOT_FOUND',
        message: 'Notification not found'
      });
    }
    res.json(updated);
  });

  // Protected: DELETE /:id
  router.delete('/:id', authMiddleware, (req, res) => {
    const userId = req.user.sub;
    const { id } = req.params;

    const deleted = repository.deleteNotification(id, userId);
    if (!deleted) {
      return res.status(404).json({
        error: 'NOT_FOUND',
        message: 'Notification not found'
      });
    }
    res.status(204).send();
  });

  return router;
}
