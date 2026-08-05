# Garage Portal — Tenant System Admin Auth

Login and invite flow for the **tenant system admin** who receives portal access when an organization is created in offstreet-service.

**Base URL (gateway):** `http://localhost:8080`  
**Keycloak realm:** `parknova` (same as consumer; isolated by role `SYSTEM_ADMIN` + claim `organizationId`)

---

## User stories

### US-01 — Receive portal access on organization creation

As a newly onboarded tenant, I want to receive an email with my portal link and credentials when my organization is created, so that I can access my garage portal without contacting support.

**Acceptance criteria**

- Given an organization is created with a unique email address  
- Then an invite email is sent to that address within 5 minutes, showing the organization name, the email as username, and the system admin role  
- And the email contains a one-time setup link valid for 72 hours  
- And no password exists on the account until the link is used

### US-02 — Set an initial password

As a tenant admin opening my setup link for the first time, I want to create my own password, so that I'm the only one who knows my credentials.

**Acceptance criteria**

- Given a valid, unused setup link  
- Then the username field shows my organization email as read-only  
- And I must enter a password meeting the complexity rules and confirm it before the account activates  
- And the link is invalidated after one successful use  
- Given an expired or already-used link  
- Then I see a clear message and an option to request a new link

### US-03 — Sign in to the garage portal

As a returning tenant admin, I want to sign in with my organization email and password, so that I can access my garage portal and manage my account with system admin privileges.

**Acceptance criteria**

- Given an activated account  
- When I enter the correct organization email and password  
- Then I land on my garage portal dashboard with system admin access  
- Given incorrect credentials  
- Then I see a generic "incorrect username or password" message, without revealing which field was wrong  
- And I can request a password reset from the login screen

### US-04 — Change password from an active session

As a signed-in tenant admin, I want to change my password from account settings, so that I can rotate my credentials on my own schedule or after a suspected compromise.

**Acceptance criteria**

- Given I am signed in  
- When I open security settings  
- Then I must confirm my current password before setting a new one  
- And the new password must meet complexity rules and not match the current password  
- Then my current session stays active and all other active sessions are signed out  
- And I receive a confirmation notice that my password was changed  
- And the session access token includes `organizationId`

---

## Happy path

```text
Platform admin                Gateway / Offstreet              Keycloak                 Tenant admin
──────────────                ───────────────────              ────────                 ────────────
POST /api/v1/organizations ─► persist org
POST /internal/auth/.../provision (platform / ops)
                              create user (no password) ──────►
                              assign SYSTEM_ADMIN + organizationId
                              email setup link (72h) ─────────────────────────────────► inbox
                                                                                    open setup link
                              GET /auth/portal/setup?token=
                              POST /auth/portal/setup-password ─► set password
                              POST /auth/portal/login ──────────► JWT
                                                                                    dashboard
                                                                                    (organizationId in token)
```

---

## Password complexity

All setup / reset / change endpoints enforce:

- Minimum 8 characters  
- At least one uppercase letter  
- At least one lowercase letter  
- At least one digit  
- At least one special character (`!@#$%^&*()_+-=[]{}|;:'",.<>/?`~\`)  
- Change-password: new password must differ from current  

Align the Keycloak realm password policy with the same rules.

---

## Keycloak configuration (`parknova` realm)

Do this once in the Keycloak Admin Console before running Postman or the portal UI.

### 1. Realm role `SYSTEM_ADMIN`

1. Open realm **`parknova`**.
2. Go to **Realm roles** → **Create role**.
3. Name: `SYSTEM_ADMIN`.
4. Description: `Garage portal tenant system administrator`.
5. Save.

### 2. Service-account permissions on `parknova-api-gateway`

The gateway provisions users via the Admin API (client credentials).

1. **Clients** → `parknova-api-gateway`.
2. **Settings / Capability config:**
   - **Client authentication:** ON  
   - **Service accounts roles:** ON  
   - **Direct access grants:** ON (needed for password login / portal login)
3. **Service account roles** tab → **Assign role** → filter by clients → **`realm-management`**:
   - `manage-users`
   - `view-users`
   - `query-users`
   - `view-realm` (needed to read/assign realm roles)
   - Prefer `manage-realm` only if the narrower roles are insufficient in your Keycloak version
4. Confirm the client secret matches `KEYCLOAK_CLIENT_SECRET` / `parknova.keycloak.client-secret` on the gateway.

### 3. Protocol mapper: `organizationId` on the access token

Without this mapper, portal login works but the JWT will **not** contain `organizationId` (and `X-Organization-Id` will be empty).

1. **Clients** → `parknova-api-gateway` → **Client scopes** (or **Dedicated** scope / **Mappers** depending on Keycloak version).
2. Add mapper → **By configuration** → **User Attribute**:
   - **Name:** `organizationId`
   - **User Attribute:** `organizationId`
   - **Token Claim Name:** `organizationId`
   - **Claim JSON Type:** `String`
   - **Add to ID token:** ON  
   - **Add to access token:** ON  
   - **Add to userinfo:** ON (optional)
3. Save.

Realm roles already appear under `realm_access.roles` by default — confirm a test login JWT includes `SYSTEM_ADMIN` after provision.

### 4. Password policy (align with gateway)

1. **Realm settings** → **Security defenses** / **Policies** → **Password policy**.
2. Match gateway rules in [Password complexity](#password-complexity):
   - Minimum length **8**
   - Digits, lowercase, uppercase, special characters
3. Save.

### 5. User profile attributes (optional but recommended)

Under **Realm settings** → **User profile** (Keycloak 24+), allow unmanaged attributes or declare:

| Attribute | Purpose |
|-----------|---------|
| `organizationId` | Tenant scope; mapped into JWT |
| `organizationName` | Shown in invite email / setup UI |

Invite/reset tokens are **not** Keycloak attributes — they live in `organization_console.portal_invite` (org-admin).

If unmanaged attributes are disabled, provision will fail when setting `organizationId` — enable unmanaged attributes or add these attributes to the user profile.

### 6. SMTP (optional)

**Either:**

- Configure **Realm settings** → **Email** (not required for the custom gateway mailer), **or**
- Set gateway `MAIL_ENABLED=true` + `spring.mail.*` so the gateway sends invite/reset emails.

When `MAIL_ENABLED=false`, the gateway **logs** the setup/reset URL — copy the `token` query param into Postman variables `setupToken` / `resetToken`.

### 7. Quick verification

After `POST /internal/auth/tenant-admin/provision`:

1. **Keycloak** → **Users** → search by org email:
   - Username = email  
   - No password until setup  
   - Role `SYSTEM_ADMIN`  
   - Attribute `organizationId`
2. **Database** (`organization_console.portal_invite`):
   - Row with `kind=SETUP`, matching email/org id, `used_at` null, `expires_at` ~72h ahead
3. After setup-password + portal login, decode the access token and confirm `organizationId` + `SYSTEM_ADMIN`.

---

## Postman

Import: [`postman/ParkNova-Garage-Portal-Auth.postman_collection.json`](./postman/ParkNova-Garage-Portal-Auth.postman_collection.json)

### Collection variables

| Variable | Example | Notes |
|----------|---------|--------|
| `baseUrl` | `http://localhost:8080` | Gateway |
| `serviceSecret` | `local-dev-secret` | Must match `SERVICE_TO_SERVICE_SECRET` |
| `email` | `admin@acme.example` | Unique per run if re-provisioning |
| `organizationId` | `42` | Used by direct provision; overwritten by create-org |
| `setupToken` / `resetToken` | _(from logs)_ | Paste token from invite/reset URL |
| `password` / `newPassword` | `SecurePass1!` / `NewSecure2!` | Must meet complexity rules |

### Suggested run order

1. **0.1 Health**
2. **1.2 Create organization** (optional) → set `organizationId`
3. **1.1 Provision** with that org id / email
4. From gateway logs, set `setupToken`
5. **2.1 Preview** → **2.2 Setup password** → **2.3** (expect 400 on reused token)
6. **3.2 Portal login** — tests assert JWT `organizationId` and `SYSTEM_ADMIN`
7. **6.x Org-admin APIs** — e.g. dashboard, users, patterns (Bearer token)
8. Optional: **4. Change password**, **5. Forgot/reset**, **7. Logout**

---

## APIs

### Provision (service-to-service)

`POST /internal/auth/tenant-admin/provision`

**Headers:** `X-SERVICE-TO-SERVICE: <shared secret>`

**Request**
```json
{
  "organizationId": 42,
  "email": "admin@acme.example",
  "organizationName": "Acme Parking"
}
```

**Response:** `201 Created`
```json
{
  "message": "Tenant admin provisioned. Invite email queued.",
  "email": "admin@acme.example",
  "organizationId": 42
}
```

Called by the platform (or Postman) after `POST /api/v1/organizations` succeeds — offstreet-service does **not** call this itself. Org create is independent of invite provision; use `POST /auth/portal/resend-invite` if needed.

---

### Validate setup link

`GET /auth/portal/setup?token={token}`

**Response (valid)**
```json
{
  "email": "admin@acme.example",
  "organizationName": "Acme Parking",
  "organizationId": 42,
  "kind": "SETUP"
}
```

**Expired / used:** `400` with message and hint to request a new link.

---

### Set initial password

`POST /auth/portal/setup-password`

```json
{
  "token": "...",
  "password": "SecurePass1!",
  "confirmPassword": "SecurePass1!"
}
```

**Response:** `200` `{ "message": "Account activated. You can now sign in." }`

---

### Resend invite

`POST /auth/portal/resend-invite`

```json
{ "email": "admin@acme.example" }
```

Always returns a generic success message (no account enumeration).

---

### Portal login

`POST /auth/portal/login`

```json
{
  "email": "admin@acme.example",
  "password": "SecurePass1!"
}
```

**Success:** standard token payload (`access_token`, `refresh_token`, …). Access token must include:

- `organizationId` (string or number claim from mapper)  
- `realm_access.roles` containing `SYSTEM_ADMIN`

**Failure:** `401` `{ "message": "Incorrect username or password" }`

Consumer `POST /auth/login` is unchanged and must **not** be used by the garage portal.

---

### Forgot / reset password

`POST /auth/portal/forgot-password` — `{ "email": "..." }` (generic success)

`POST /auth/portal/reset-password` — `{ "token", "password", "confirmPassword" }`

---

### Change password (authenticated)

`POST /auth/portal/change-password`  
**Header:** `Authorization: Bearer <access_token>`

```json
{
  "currentPassword": "SecurePass1!",
  "newPassword": "NewSecure2!",
  "confirmPassword": "NewSecure2!"
}
```

Current session (`sid`) remains; other Keycloak sessions are revoked. Confirmation message returned.

---

### Logout

`POST /auth/logout` — `{ "refreshToken": "..." }` (shared with consumer)

---

## Gateway → org-admin (garage portal APIs)

All `parknova-org-admin` APIs are exposed through the gateway under **`/org-admin/**`** (JWT required).

| Gateway path | Downstream (org-admin :8091) |
|--------------|------------------------------|
| `GET/POST/PUT … /org-admin/api/v1/...` | `/api/v1/...` (`StripPrefix=1`) |

**Auth rules**

1. `Authorization: Bearer <portal access_token>` (from `POST /auth/portal/login`)
2. Token must include realm role **`SYSTEM_ADMIN`**
3. Token must include claim **`organizationId`**
4. If the client sends `?organizationId=`, it must match the token; otherwise the gateway injects the token’s org id
5. Downstream headers: `Authorization`, `X-Organization-Id`, `X-Actor-Name` (email/username), `X-Parknova-Gateway`

**Examples**

```http
GET /org-admin/api/v1/dashboard/summary
Authorization: Bearer <access_token>
```

```http
GET /org-admin/api/v1/permanent-users?page=0&size=20
Authorization: Bearer <access_token>
```

```http
GET /org-admin/api/v1/patterns
Authorization: Bearer <access_token>
```

Do **not** call org-admin on `:8091` from the browser. Offstreet platform APIs remain on `/api/v1/**` (separate from the garage portal).

Env: `ORG_ADMIN_SERVICE_URI` (default `http://localhost:8091`). Gateway also calls org-admin **directly** (not via `/org-admin`) for invite token CRUD at `/internal/v1/portal-invites/**` using `SERVICE_TO_SERVICE_SECRET`.

### Invite token storage

| Column | Notes |
|--------|--------|
| `organization_console.portal_invite` | One-time SETUP/RESET tokens |
| `token` | UUID in setup/reset email link |
| `expires_at` | Default 72h |
| `used_at` / `invalidated_at` | Cleared for active tokens; resend invalidates prior active rows for same email+kind |

---

## Gateway → downstream identity

After JWT validation, [`ForwardJwtHeadersFilter`](../src/main/java/com/parknova/parknovaapigateway/config/ForwardJwtHeadersFilter.java) injects:

| Header | Source |
|--------|--------|
| `X-User-Sub` | `sub` |
| `X-User-Username` | `preferred_username` |
| `X-User-Email` | `email` |
| `X-Organization-Id` | claim `organizationId` |
| `X-Actor-Name` | `email` / `preferred_username` (org-admin audit) |
| `X-Parknova-Gateway` | `1` |

Garage portal backends must authorize using `SYSTEM_ADMIN` + `X-Organization-Id` / JWT `organizationId`, never a client-supplied org id alone.

---

## Configuration

| Property / env | Purpose |
|----------------|---------|
| `parknova.portal.base-url` / `PORTAL_BASE_URL` | Base URL for setup/reset links |
| `parknova.portal.invite-ttl-hours` | Default `72` |
| `parknova.service-to-service-secret` / `SERVICE_TO_SERVICE_SECRET` | Shared with offstreet |
| `parknova.mail.enabled` / `MAIL_ENABLED` | When `false`, invite URL is logged instead of emailed |
| `parknova.mail.from` | From address when mail enabled |
| Spring `spring.mail.*` | SMTP host/port/credentials |
| `ORG_ADMIN_SERVICE_URI` | Org-admin base URL (default `http://localhost:8091`) |

Offstreet-service has no portal auth or provision callback — invite provisioning is done via the gateway internal API only.
