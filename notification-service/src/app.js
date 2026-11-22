import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import swaggerUi from 'swagger-ui-express';
import { openApiSpec } from './docs/openapi.js';
import { createNotificationRoutes } from './routes/notificationRoutes.js';
import { createAuthMiddleware } from './middleware/authMiddleware.js';
import { metricsMiddleware, getMetrics, getMetricsContentType } from './metrics.js';

export function createApp({ config, repository, getHealthStatus, authMiddlewareOverride = null }) {
  const app = express();

  // Helmet with permissive CSP so Swagger UI can load CDN/inline assets while keeping nosniff
  app.use(helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
  }));

  // CORS configuration
  app.use(cors({
    origin: (origin, callback) => {
      // Allow requests with no origin (like mobile apps, curl, server-to-server)
      if (!origin) return callback(null, true);
      const allowed = config.corsAllowedOrigins.includes('*') || config.corsAllowedOrigins.some(o => o === origin);
      callback(null, allowed ? true : true); // allow or reflect
    },
    credentials: true
  }));

  app.use(express.json());

  // Prometheus metrics middleware
  app.use(metricsMiddleware);

  // Prometheus scrape endpoint
  app.get('/metrics', async (req, res) => {
    try {
      res.setHeader('Content-Type', getMetricsContentType());
      res.send(await getMetrics());
    } catch (err) {
      res.status(500).send(err.message);
    }
  });

  // Swagger Documentation
  app.use('/api/notifications/docs', swaggerUi.serve, swaggerUi.setup(openApiSpec));

  // Authentication Middleware
  const authMiddleware = authMiddlewareOverride || createAuthMiddleware({
    jwkSetUri: config.keycloakJwkSetUri,
    issuerUris: [
      config.keycloakIssuerUri,
      'http://localhost:8180/realms/mindspark',
      'http://127.0.0.1:8180/realms/mindspark'
    ]
  });

  // Mount API Routes
  const notificationRoutes = createNotificationRoutes({
    repository,
    authMiddleware,
    getHealthStatus
  });

  app.use('/api/notifications', notificationRoutes);

  // Global 404 handler
  app.use((req, res) => {
    res.status(404).json({ error: 'NOT_FOUND', message: `Route ${req.method} ${req.path} not found` });
  });

  // Global Error handler
  app.use((err, req, res, next) => {
    console.error('[Error]', err);
    res.status(500).json({ error: 'INTERNAL_SERVER_ERROR', message: err.message || 'An unexpected error occurred' });
  });

  return app;
}
