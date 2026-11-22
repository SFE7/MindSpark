import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';

const otelEndpoint = process.env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT;

if (otelEndpoint) {
  const traceExporter = new OTLPTraceExporter({
    url: otelEndpoint
  });

  const sdk = new NodeSDK({
    serviceName: 'notification-service',
    traceExporter,
    instrumentations: [
      getNodeAutoInstrumentations({
        '@opentelemetry/instrumentation-fs': { enabled: false },
        '@opentelemetry/instrumentation-dns': { enabled: false }
      })
    ]
  });

  sdk.start();
  console.log(`[Notification Service] OpenTelemetry tracing initialized -> ${otelEndpoint}`);

  process.on('SIGTERM', () => {
    sdk.shutdown()
      .then(() => console.log('[Notification Service] OTel tracing stopped'))
      .catch((err) => console.error('[Notification Service] Error stopping OTel', err));
  });
}
