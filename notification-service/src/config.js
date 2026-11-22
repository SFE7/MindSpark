export const config = {
  port: parseInt(process.env.PORT || '8086', 10),
  dbPath: process.env.NOTIFICATION_DB_PATH || '/data/notifications.db',
  kafkaBootstrapServers: (process.env.KAFKA_BOOTSTRAP_SERVERS || 'kafka:9092').split(',').map(s => s.trim()),
  eurekaUrl: (process.env.EUREKA_URL || 'http://discovery-server:8761/eureka').replace(/\/$/, ''),
  eurekaAppName: 'NOTIFICATION-SERVICE',
  eurekaHostName: process.env.EUREKA_INSTANCE_HOSTNAME || 'notification-service',
  eurekaIpAddr: process.env.EUREKA_INSTANCE_IP || 'notification-service',
  keycloakJwkSetUri: process.env.KEYCLOAK_JWK_SET_URI || 'http://keycloak:8080/realms/mindspark/protocol/openid-connect/certs',
  keycloakIssuerUri: process.env.KEYCLOAK_ISSUER_URI || 'http://localhost:8180/realms/mindspark',
  corsAllowedOrigins: (process.env.CORS_ALLOWED_ORIGINS || 'http://localhost:4200,http://127.0.0.1:4200').split(',').map(s => s.trim())
};
