# Keycloak Role Sync Setup Guide

This guide describes how to configure `subscription-service` to automatically assign or remove the `premium` Keycloak realm role for users when they subscribe, upgrade, or cancel plans.

## Overview
When `subscription-service` detects a paid plan change (e.g. subscribing to `PREMIUM` or `TEAM`), it calls Keycloak's Admin REST API to add the `premium` realm role to the user's account. When canceled or downgraded to `FREE`, it removes the role.

> [!NOTE]
> If credentials are NOT configured, `subscription-service` continues operating normally without crashing; it logs a warning and returns `roleSyncStatus: "SKIPPED_UNCONFIGURED"`.

---

## 3-Step Setup in Keycloak Admin Console

### Step 1: Create a Service Account Client
1. Open the Keycloak Admin Console (`http://localhost:8180/admin/master/console/` or `http://<HOST_IP>:8180/admin/`).
2. Switch to the **`mindspark`** realm in the top-left dropdown.
3. Go to **Clients** -> **Create client**:
   - **Client type**: OpenID Connect
   - **Client ID**: `subscription-service`
   - Click **Next**.
4. Capability config:
   - **Client authentication**: **On** (Confidential)
   - **Authorization**: Off
   - **Authentication flow**: Check **Service accounts roles**
   - Click **Save**.

### Step 2: Assign `manage-users` Role to the Service Account
1. On the client details page for `subscription-service`, click the **Service account roles** tab.
2. Click **Assign role**.
3. Change filter dropdown from "Filter by realm roles" to **Filter by clients**.
4. Search for `realm-management`.
5. Select:
   - `manage-users`
   - `view-realm`
6. Click **Assign**.

### Step 3: Copy Client Secret and Configure Environment
1. In the `subscription-service` client page, switch to the **Credentials** tab.
2. Copy the **Client Secret**.
3. Provide the following environment variables to `subscription-service`:
   ```bash
   KEYCLOAK_ADMIN_BASE_URL=http://keycloak:8080
   KEYCLOAK_ADMIN_REALM=mindspark
   KEYCLOAK_ADMIN_CLIENT_ID=subscription-service
   KEYCLOAK_ADMIN_CLIENT_SECRET=<COPIED_CLIENT_SECRET>
   ```

---

## Verification
- When a user on `FREE` subscribes to `PREMIUM`, the `premium` realm role is assigned.
- Upon Keycloak token refresh (`keycloak.updateToken(-1)`), the user's JWT `realm_access.roles` contains `premium`.
- `ai-service` immediately allows 20 daily AI requests instead of 10.
