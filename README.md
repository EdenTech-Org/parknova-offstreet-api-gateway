# ParkNova API Gateway

Spring Cloud Gateway MVC with Keycloak-backed **User Account Management** (register, email OTP verification, login, password reset, logout) and JWT protection for downstream routes.

## Stack

- Java 21
- Spring Boot 3.3.5
- Spring Cloud 2023.0.3 (`spring-cloud-starter-gateway-mvc`)
- Spring Security OAuth2 Resource Server (JWT)
- Keycloak Admin Client 26.x
- Spring Mail (OTP delivery)

## Run locally

```bash
cd d:\project\parknova-api-gateway

# set secrets / Keycloak
set KEYCLOAK_BASE_URL=https://keycloak-dev.eden-tech.io
set KEYCLOAK_REALM=parknova
set KEYCLOAK_CLIENT_ID=parknova-api-gateway
set KEYCLOAK_CLIENT_SECRET=<secret-from-keycloak>
set SPRING_MAIL_HOST=localhost
set SPRING_MAIL_PORT=1025

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Default port: `8080`.

If SMTP is down, registration still succeeds and the OTP is written to application logs (dev-friendly).

## Auth API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | Public | Register with username, email, phone, password → sends email OTP |
| POST | `/auth/verify-email` | Public | `{ "email", "otp" }` → marks email verified in Keycloak |
| POST | `/auth/resend-otp` | Public | Resend email verification OTP |
| POST | `/auth/login` | Public | `{ "username", "password" }` → JWT `access_token` + `refresh_token` |
| POST | `/auth/forgot-password` | Public | Send password-reset OTP to email |
| POST | `/auth/reset-password` | Public | `{ "email", "otp", "newPassword" }` |
| POST | `/auth/logout` | Public | `{ "refreshToken" }` → revoke session |

Login is rejected until email is verified.

Example register:

```json
POST /auth/register
{
  "username": "jdoe",
  "email": "jdoe@example.com",
  "phone": "+966500000000",
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
   - Configure SMTP (optional for Keycloak-native mail; gateway OTP uses `spring.mail.*`)

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

Open client → **Service account roles** → assign from client `realm-management`:

- `manage-users`
- `view-users`
- `query-users`

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

- Required actions: ensure **Verify Email** exists (gateway sets `emailVerified` via Admin API after OTP; Keycloak required-action flow is optional)
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
| `SPRING_MAIL_HOST` / `PORT` / `USERNAME` / `PASSWORD` | SMTP for OTP |
| `MAIL_FROM` | `noreply@parknova.io` |

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

Postman: `parknova-offstreet-mobile-service/docs/postman/ParkNova-Mobile-Consumer.postman_collection.json`

