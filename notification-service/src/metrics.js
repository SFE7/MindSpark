import client from 'prom-client';

// Collect default Node.js runtime metrics
client.collectDefaultMetrics({ register: client.register });

export const httpRequestsTotal = new client.Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests processed',
  labelNames: ['method', 'route', 'status_code']
});

export const httpRequestDurationSeconds = new client.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Histogram of HTTP request durations in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10]
});

export function metricsMiddleware(req, res, next) {
  const start = process.hrtime();

  res.on('finish', () => {
    const diff = process.hrtime(start);
    const durationSeconds = diff[0] + diff[1] / 1e9;
    
    // Normalize route label so dynamic IDs do not cause label explosion
    const route = req.baseUrl || req.route?.path || req.path || 'unknown';
    const statusCode = res.statusCode ? res.statusCode.toString() : '500';
    const method = req.method;

    httpRequestsTotal.inc({ method, route, status_code: statusCode });
    httpRequestDurationSeconds.observe({ method, route, status_code: statusCode }, durationSeconds);
  });

  next();
}

export async function getMetrics() {
  return await client.register.metrics();
}

export function getMetricsContentType() {
  return client.register.contentType;
}
