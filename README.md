# ParkNova API Gateway

Spring Cloud Gateway MVC with Keycloak-backed **User Account Management** (register, login, logout) and JWT protection for downstream routes.

## Stack

- Java 21
- Spring Boot 3.3.5
- Spring Cloud 2023.0.3 (`spring-cloud-starter-gateway-mvc`)
- Spring Security OAuth2 Resource Server (JWT)
- Keycloak Admin Client 26.x

## Run locally

```bash
cd d:\project\parknova-offstreet-api-gateway

# set secrets / Keycloak
set KEYCLOAK_BASE_URL=https://keycloak-dev.eden-tech.io
set KEYCLOAK_REALM=parknova
set KEYCLOAK_CLIENT_ID=parknova-api-gateway
set KEYCLOAK_CLIENT_SECRET=<secret-from-keycloak>

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Default port: `8080`.

### Postman (full consumer flow)

Import `docs/postman/ParkNova-API-Gateway.postman_collection.json` and run folders **in order**:

1. Health  
2. Auth — Register → Login  
3. Profile — Bootstrap → Me  
4. Lookups → Vehicles  
5. Garages  
6. Parking happy path — Prepare → ANPR check-in → Active → check-out/end → Pay  
7. History & notifications  
8. Logout  

Requires gateway `:8080`, mobile-service `:8081`, and offstreet `:8090`. Set `serviceSecret` for ANPR requests.
## Auth API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | Public | Create user in Keycloak (ready to log in) |
| POST | `/auth/login` | Public | `{ "email", "password" }` → JWT `access_token` + `refresh_token` |
| POST | `/auth/logout` | Public | `{ "refreshToken" }` → revoke session |

Example register:

```json
POST /auth/register
{
  "username": "jdoe",
  "email": "jdoe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+966500000000",
  "password": "SecurePass1!"
}
```

Example login:

```json
POST /auth/login
{
  "email": "jdoe@example.com",
  "password": "SecurePass1!"
}
```

Example login response:

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  "scope": "openid"
}
```

Call protected routes with:

```http
Authorization: Bearer <access_token>
```

## Gateway routes

| Path | Auth at gateway | Target (env) |
|------|-----------------|--------------|
| `/auth/**` | Public | Gateway itself (Keycloak) |
| `/client/**` | **JWT required** | `MOBILE_SERVICE_URI` (default `http://localhost:8081`) |
| `/internal/v1/sessions/**` | Public (service header checked downstream) | `MOBILE_SERVICE_URI` |
| `/mobile/**` | JWT | `MOBILE_SERVICE_URI` (alias) |
| `/api/v1/**`, `/customer/**` | JWT | `OFFSTREET_SERVICE_URI` (default `http://localhost:8090`) |
| `/external/**` | JWT | `OFFSTREET_SERVICE_URI` (operator/external catalog; mobile uses Feign direct) |
| `/swagger-ui/**`, `/api-docs/**` | Public | Passthrough when routed to mobile |

### Mobile / ANPR env vars

| Variable | Purpose |
|----------|---------|
| `MOBILE_SERVICE_URI` | Mobile consumer service base URL |
| `OFFSTREET_SERVICE_URI` | Offstreet operator/catalog service |
| `SERVICE_TO_SERVICE_SECRET` | Must match mobile `parknova.internal.service-to-service-secret` (used by gate callers, not by gateway) |

---

## Keycloak configuration checklist

### 1. Realm

1. Create realm: **`parknova`**
2. Realm settings → **Login**
   - User registration: **OFF** (gateway owns register)
   - Login with email: **ON**
3. Realm settings → **Tokens**
   - Access token lifespan: e.g. 5–15 minutes
   - Refresh token / SSO session timeouts as needed
4. Realm settings → **Email**
   - Optional (not used by this gateway anymore)

### 2. Confidential client `parknova-api-gateway`

Create client:

| Setting | Value |
|---------|--------|
| Client type | Confidential |
| Client authentication | ON |
| Service accounts | **ON** |
| Direct access grants | **ON** (required for username/password login) |
| Standard flow | Optional (browser login later) |
| Valid redirect URIs | Not required for password grant |

Copy the **Client secret** into `KEYCLOAK_CLIENT_SECRET`.

### 3. Service account roles

Open client → **Capability config**:

| Setting | Value |
|---------|--------|
| Client authentication | **ON** |
| Service accounts roles | **ON** |
| Direct access grants | **ON** (for `/auth/login`) |

Open client → **Client scopes** (or Settings):

| Setting | Value |
|---------|--------|
| Full scope allowed | **ON** (required so `realm-management` roles appear on client_credentials tokens) |

Open client → **Service account roles** → **Assign role**:

1. Filter by **clients** (not realm roles)
2. Select client **`realm-management`**
3. Assign at least: `manage-users`, `view-users`, `query-users`  
   (or assign `realm-admin` for full Admin API access)

Verify with:

```powershell
$env:KEYCLOAK_CLIENT_SECRET = "<secret>"
.\scripts\check-keycloak-admin.ps1
```

Restart the gateway after roles change (tokens are cached briefly by the admin client).

(Do not assign broader roles unless required.)

### 4. User profile / attributes (required for mobile)

- Allow custom attribute **`phone`** (User Profile → Attributes) so registration can store phone numbers.
- Make `phone` available in tokens (see mappers below).

### 5. Protocol mappers on `parknova-api-gateway` (access token)

Add / enable these mappers so mobile-service bootstrap can read claims:

| Mapper | Claim | Notes |
|--------|-------|--------|
| User Property — Username | `preferred_username` | Built-in |
| User Property — Email | `email` | Built-in |
| User Attribute — `phone` | `phone` | Token claim name `phone` |
| Audience (optional) | `aud` | Include `parknova-mobile` if you introduce a public mobile client later |

Mobile-service resolves identity from: `sub`, `preferred_username`/`username`, `email`, `phone`/`phone_number`.

### 6. Optional realm role `CONSUMER`

1. Realm roles → create **`CONSUMER`**
2. Default roles → add `CONSUMER` (or assign on gateway register via Admin API later)
3. Optional client scope mapper: realm roles → `realm_access.roles`

Gateway JWT validation currently only requires a valid signature/issuer — role checks can be added later.

### 7. Authentication / security

- Required actions: leave **Verify Email** Default Action **OFF** (users are created with Email verified = OFF, but without VERIFY_EMAIL required action so login still works)
- Authentication → **Policies**: set password policy (min length, digits, etc.)
- Security defenses → **Brute force detection**: **ON**

### 8. URLs used by this gateway

```
Issuer:  {KEYCLOAK_BASE_URL}/realms/parknova
Token:   .../protocol/openid-connect/token
Logout:  .../protocol/openid-connect/logout
Revoke:  .../protocol/openid-connect/revoke
JWKS:    .../protocol/openid-connect/certs
Admin:   {KEYCLOAK_BASE_URL}/admin/realms/parknova/users
```

### 9. Environment variables

| Variable | Example |
|----------|---------|
| `KEYCLOAK_BASE_URL` | `https://keycloak-dev.eden-tech.io` |
| `KEYCLOAK_REALM` | `parknova` |
| `KEYCLOAK_CLIENT_ID` | `parknova-api-gateway` |
| `KEYCLOAK_CLIENT_SECRET` | *(from Keycloak)* |
| `KEYCLOAK_ISSUER_URI` | optional override of issuer |
| `MOBILE_SERVICE_URI` | `http://localhost:8081` |
| `OFFSTREET_SERVICE_URI` | `http://localhost:8090` |

### 10. Optional public client `parknova-mobile`

Only if the app will talk to Keycloak directly (PKCE). Keep Direct Access Grants **OFF** if all login goes through this gateway.

| Setting | Value |
|---------|--------|
| Client type | Public |
| PKCE | Required / S256 |
| Direct access grants | OFF |
| Standard flow | ON |
| Valid redirect URIs | Your app deep links / localhost |

For the current architecture, **prefer gateway `/auth/login`** so credentials never hit a public native client.

---

## Mobile integration

See consumer guide in the mobile service repo:

`parknova-offstreet-mobile-service/docs/MOBILE-INTEGRATION.md`

Gateway auth Postman (this repo): `docs/postman/ParkNova-API-Gateway.postman_collection.json`  
(same E2E collection is mirrored in `parknova-offstreet-mobile-service-backend/docs/postman/`)

Mobile consumer Postman: `parknova-offstreet-mobile-service-backend/docs/postman/ParkNova-Mobile-Consumer.postman_collection.json`

