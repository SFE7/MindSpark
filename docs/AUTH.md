# MindSpark Authentication Architecture

## 1. Running the Auth Stack Standalone
To start Keycloak and its dedicated database independently:
```bash
docker compose -f infra/auth/compose.yaml up -d
```
Keycloak runs on host port `8180` and uses an internal network (`auth_net`) with its own PostgreSQL instance (`keycloak-db`). The application database is completely separate.

## 2. Environment Variables
All configuration is loaded from `.env`:
- `HOST_IP`: Host Wi-Fi hotspot IP address (e.g. `192.168.0.134`).
- `KEYCLOAK_DB_USER` / `KEYCLOAK_DB_PASSWORD`: Keycloak PostgreSQL credentials.
- `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`: Keycloak administrator bootstrap login.
- `KEYCLOAK_ISSUER_URI`: Expected JWT token issuer URL (`http://${HOST_IP}:8180/realms/mindspark`).
- `KEYCLOAK_JWK_SET_URI`: Internal endpoint for token verification keys (`http://keycloak:8080/realms/mindspark/protocol/openid-connect/certs`).
- `CORS_ALLOWED_ORIGINS`: Allowed browser origins (`http://localhost:4200,http://${HOST_IP}:4200`).

## 3. Token Claims
The backend relies exclusively on standard JWT claims:
- `sub`: Keycloak user UUID; stored as `keycloak_id` in the database to map identity.
- `preferred_username`: User display name / handle.
- `email`: User email address.
- `realm_access.roles`: List of realm roles mapped to Spring `GrantedAuthority` with prefix `ROLE_`.
Keycloak acts as the identity provider; business data and room memberships remain in the application database.

## 4. Issuer Matching & LAN Resolution
- Browsers on the LAN obtain tokens from `http://${HOST_IP}:8180/realms/mindspark`.
- Keycloak sets the `iss` claim to `http://${HOST_IP}:8180/realms/mindspark`.
- The backend verifies that the token's `iss` matches `KEYCLOAK_ISSUER_URI`.
- The backend fetches JWKS public keys over Docker's internal network via `KEYCLOAK_JWK_SET_URI` (`http://keycloak:8080/...`), bypassing host firewall or hairpinning limitations.

## 5. Microservice Integration Steps
To integrate a new microservice with Keycloak:
1. Join `auth_net` in `docker-compose.yml`.
2. Configure `KEYCLOAK_ISSUER_URI` and `KEYCLOAK_JWK_SET_URI`.
3. Copy the reusable security package (`com.sme.maintenance.security`).
4. Validate incoming JWTs locally without calling Keycloak per request.
