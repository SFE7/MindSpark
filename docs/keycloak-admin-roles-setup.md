# MindSpark Keycloak Admin Roles & Provisioning Setup Guide

This guide describes how to configure the `admin` and `super-admin` realm roles in Keycloak for the `mindspark` realm and configure automated admin account provisioning.

---

## 1. Quick Operator Setup: Assign Admin Roles via Keycloak Web Console

You do NOT need to modify `mindspark-realm.json`. You can assign roles directly in your running Keycloak instance.

1. Open Keycloak Administration Console in your browser:
   `http://localhost:8180` (or `http://<HOST_IP>:8180`)
2. Sign in as the Keycloak master administrator:
   - **Username**: `admin`
   - **Password**: `admin` (or the password set in your `.env` `KEYCLOAK_ADMIN_PASSWORD`)
3. Select the **`mindspark`** realm from the realm dropdown in the top-left corner.

### Create Realm Roles (if not already created)
1. In the left navigation menu, click **Realm roles**.
2. Click **Create role**:
   - **Role name**: `admin`
   - **Description**: MindSpark administrator with access to plans, subscriptions, and platform statistics.
   - Click **Save**.
3. Click **Create role** again:
   - **Role name**: `super-admin`
   - **Description**: MindSpark super administrator with full system access and administrator provisioning.
   - Click **Save**.

### Assign Admin Role to a Test User
1. In the left navigation menu, click **Users**.
2. Search for your user (e.g. `testuser` or by email) and click on their username.
3. Open the **Role mapping** tab.
4. Click **Assign role**.
5. Filter by **Realm roles** and select:
   - Check `admin` (to test regular administrator permissions), OR
   - Check `super-admin` (to test super-administrator account provisioning).
6. Click **Assign**.
7. The user can now navigate to `http://localhost:4200/admin` or `http://localhost:4200/admin/login`.

---

## 2. Automated Admin Account Provisioning Setup (Story 65)

`subscription-service` supports automated Keycloak admin provisioning (`POST /api/admin/admins`) using the Keycloak Admin REST API.

To enable automated provisioning from the application without manual Keycloak UI steps:

### Create a Service Account Client in Keycloak
1. In the `mindspark` realm, click **Clients** -> **Create client**.
2. **Client type**: `OpenID Connect`
3. **Client ID**: `mindspark-admin-cli`
4. Click **Next**.
5. Enable **Client authentication** (ON).
6. Check **Service accounts roles** (ON) and **Standard flow** (OFF).
7. Click **Save**.
8. Go to the **Credentials** tab and copy the **Client Secret**.
9. Go to the **Service accounts roles** tab -> Click **Assign role** -> Filter by **Filter by clients**:
   - Select `realm-management` client roles:
     - `manage-users`
     - `view-users`
     - `manage-realm`
   - Click **Assign**.

### Configure `subscription-service`
Provide the client credentials to `subscription-service` via environment variables:
```bash
KEYCLOAK_ADMIN_CLIENT_ID=mindspark-admin-cli
KEYCLOAK_ADMIN_CLIENT_SECRET=<copied_client_secret>
KEYCLOAK_ADMIN_BASE_URL=http://keycloak:8080
KEYCLOAK_ADMIN_REALM=mindspark
```
When configured, super-admins can directly provision new administrators via the **Admin Accounts** UI tab at `/admin/admins`. If unconfigured, the application gracefully surfaces HTTP 503 (`KEYCLOAK_ADMIN_NOT_CONFIGURED`) with a pointer to this document.

---

## 3. Role Hierarchy & Permission Matrix

| Role | Access Level | Permitted Endpoints |
|------|-------------|---------------------|
| `super-admin` | Super Administrator | All `/api/admin/**` endpoints, including `POST/GET /api/admin/admins` |
| `admin` | Administrator | `/api/admin/plans/**`, `/api/admin/subscriptions/**`, `/api/admin/stats/**`, `/api/admin/users` (403 on `/api/admin/admins`) |
| `user` (or none) | Regular User | 403 on all `/api/admin/**` endpoints |
| Unauthenticated | Public / Guest | 401 on all `/api/admin/**` endpoints |
