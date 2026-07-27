# ParkNova — Consumer + Enforcer Integration Journey

> **Flutter teams:** use the step-by-step guide with full request/response JSON:  
> **[INTEGRATION-STEPS-CONSUMER-ENFORCER.md](./INTEGRATION-STEPS-CONSUMER-ENFORCER.md)**

End-to-end overview for integrating the **Consumer Flutter app** and the **Enforcer (workforce) Flutter app** with the API Gateway and mobile service.

**Base URL (gateway):** `http://localhost:8080`  
**Auth realms:**
| App | Keycloak realm | How to get token |
|-----|----------------|------------------|
| Consumer | `parknova` | `POST /auth/register` → `POST /auth/login` |
| Enforcer | `eden-crm-sec-users` | Keycloak token endpoint for that realm (set `enforcer_access_token`) |

**Related services:** mobile `:8081`, offstreet `:8090`, org-admin `:8091`, Kafka `${KAFKA_URL}`.

---

## 1. Big picture

```text
Consumer Flutter                    Enforcer Flutter
      │                                   │
      │ JWT (parknova)                    │ JWT (eden-crm-sec-users)
      ▼                                   ▼
 API Gateway :8080 ──────────────────────────────
      │ /auth/**          │ /client/**    │ /enforcer/**
      ▼                   ▼               ▼
 Keycloak            Mobile service ──────┼──► Org-admin (plate → employee/visitor)
                         │                │
                         ├──► Offstreet (garages, capacity, pricing)
                         └──► Kafka (session / capacity / payment events)
```

**Rule:** Consumer app **never** calls check-in/check-out. Gate entry/exit is **Enforcer-only**. Consumer prepares, shows the timer, and pays.

---

## 2. Happy-path sequence (consumer parking)

```text
CONSUMER                         ENFORCER                         BACKEND
────────                         ────────                         ───────
Register / Login
Bootstrap profile
Create vehicle (plate)
List garages / availability
Prepare session ──────────────────────────────────────────────► PENDING_ENTRY
                                 Assign garage/zone/gate
                                 Check IN (plate)
                                 (auto or confirm enter) ─────► ACTIVE + capacity−1
                                                                    Kafka SESSION_STARTED
Poll active / show timer ◄──────────────────────────────────── SESSION_STARTED
                                 …
                                 Check OUT
                                 (consumer → proforma) ───────► AWAITING_PAYMENT + invoice
                                                                    Kafka INVOICE_CREATED
Pay invoice (always PAID) ────────────────────────────────────► invoice PAID
                                                                    Kafka INVOICE_PAID
                                 Exit confirm ────────────────► ENDED + capacity+1
                                                                    Kafka SESSION_ENDED
History / notifications
Logout
```

**Corporate (employee/visitor):** same Enforcer check, but org-admin returns `segment=CORPORATE` → Shared / Dedicated / Allocation pools; usually **no invoice** on exit.

---

## 3. Consumer app journey + APIs

### 3.1 Register

`POST /auth/register`

**Request**
```json
{
  "username": "jdoe",
  "email": "jdoe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+966500000000",
  "password": "SecurePass1!"
}
```

**Response:** `201 Created` (or `409` if user exists)

---

### 3.2 Login

`POST /auth/login`

**Request**
```json
{
  "email": "jdoe@example.com",
  "password": "SecurePass1!"
}
```

**Response**
```json
{
  "access_token": "eyJhbGciOi…",
  "refresh_token": "eyJhbGciOi…",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  "scope": "openid profile email"
}
```

Use header: `Authorization: Bearer {{access_token}}` on all `/client/**` calls.

---

### 3.3 Bootstrap / Me

`POST /client/v1/me/bootstrap` — create/link consumer profile from JWT  
`GET /client/v1/me` — profile  
`PUT /client/v1/me` — update locale/phone/etc.  
`GET /client/v1/support` — support contacts  

**Bootstrap response (example)**
```json
{
  "id": 1,
  "username": "jdoe",
  "email": "jdoe@example.com",
  "phone": "+966500000000",
  "locale": "ar",
  "status": "ACTIVE"
}
```

---

### 3.4 Lookups (before vehicle create)

| Method | Path |
|--------|------|
| GET | `/client/v1/lookups/vehicle-types` |
| GET | `/client/v1/lookups/model-types` |
| GET | `/client/v1/lookups/license-types` |

**Response item**
```json
{ "id": 1, "code": "SEDAN", "nameAr": "…", "nameEn": "Sedan" }
```

---

### 3.5 Vehicles

Same attributes as org-admin. `POST /client/v1/vehicles`

**Request**
```json
{
  "plate": "1234-ABC",
  "size": "NORMAL",
  "registrationType": "PRIVATE_CARS"
}
```

**Response `201`**
```json
{
  "id": 9,
  "plate": "1234-ABC",
  "size": "NORMAL",
  "registrationType": "PRIVATE_CARS",
  "active": true
}
```

Also: `GET /client/v1/vehicles`, `PUT /client/v1/vehicles/{id}`, `POST .../activate` (alias `.../default`), `DELETE .../{id}`.

Max 3 vehicles. Plate format: `digits-letters` (e.g. `1234-ABC`).

---

### 3.6 Garages & availability

| Method | Path | Notes |
|--------|------|--------|
| GET | `/client/v1/garages?lat=&lng=&radiusKm=` | Map list + products |
| GET | `/client/v1/garages/{id}` | Detail |
| GET | `/client/v1/garages/{id}/availability` | Free slots badge |
| GET | `/client/v1/garages/{id}/directions?originLat=&originLng=` | Directions |

**Availability response**
```json
{
  "garageId": 1,
  "evaluatedAt": "2026-07-27T10:00:00Z",
  "hasFreeCapacity": true,
  "status": "AVAILABLE",
  "freeSlots": 12,
  "occupied": 8,
  "totalCapacity": 20,
  "periodId": 100,
  "zoneId": 10,
  "businessModelType": "QUOTA",
  "timeFrom": "08:00",
  "timeTo": "18:00"
}
```

Pick `productId` from garage card `zones[].periods[].products[]`.

---

### 3.7 Prepare session (before arrival)

`POST /client/v1/sessions/prepare`

**Request**
```json
{
  "garageId": 1,
  "productId": 5,
  "vehicleId": 9
}
```

**Response**
```json
{
  "id": 77,
  "garageId": 1,
  "zoneId": 10,
  "vehicleId": 9,
  "productId": 5,
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "status": "PENDING_ENTRY",
  "registrationType": null,
  "admissionPool": "CONSUMER",
  "permissionType": "NONE",
  "entryGateId": null,
  "exitGateId": null,
  "startedAt": null,
  "endedAt": null,
  "elapsedSeconds": 0,
  "timerRunning": false,
  "estimatedCost": 0.0,
  "currency": "SAR",
  "invoiceId": null,
  "invoiceStatus": null,
  "serverTime": "2026-07-27T09:55:00Z"
}
```

UI: “Ready for arrival” — wait for gate check-in.

---

### 3.8 Active session / timer (after Enforcer check-in)

`GET /client/v1/sessions/active`

Poll every few seconds (or on FCM `SESSION_STARTED`).

**Response (timer running)**
```json
{
  "id": 77,
  "garageId": 1,
  "zoneId": 10,
  "vehicleId": 9,
  "productId": 5,
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "status": "ACTIVE",
  "admissionPool": "CONSUMER",
  "permissionType": "NONE",
  "entryGateId": "ENTRY-1",
  "startedAt": "2026-07-27T10:00:00Z",
  "endedAt": null,
  "elapsedSeconds": 125,
  "timerRunning": true,
  "estimatedCost": 5.0,
  "currency": "SAR",
  "invoiceId": null,
  "invoiceStatus": null,
  "serverTime": "2026-07-27T10:02:05Z"
}
```

**Flutter timer tip:**  
`displayElapsed = (now - startedAt)` synced with `serverTime` / `elapsedSeconds`. Keep running while `timerRunning == true` (`ACTIVE` or `AWAITING_PAYMENT`).

When status becomes `AWAITING_PAYMENT`, show pay CTA using `invoiceId`.

---

### 3.9 Invoices & pay (always success stub)

`GET /client/v1/invoices/{id}`  
`GET /client/v1/invoices?status=&page=&size=`  
`POST /client/v1/invoices/{id}/pay`

**Pay request**
```json
{ "paymentMethod": "CARD" }
```

**Pay response (always)**
```json
{
  "paymentId": 9,
  "status": "PAID",
  "invoiceStatus": "PAID",
  "providerRef": "STUB-CARD-…"
}
```

Already-paid and missing-invoice cases still return `PAID` success (stub).

---

### 3.10 Notifications & history

`GET /client/v1/notifications?page=&size=`  
`POST /client/v1/notifications/{id}/read`  
`GET /client/v1/sessions?page=&size=` — history  

Notification types: `SESSION_STARTED`, `PAYMENT_REQUIRED`, `SESSION_ENDED`.

---

### 3.11 Logout

`POST /auth/logout`

**Request**
```json
{ "refreshToken": "{{refresh_token}}" }
```

---

## 4. Enforcer app journey + APIs

All under `/enforcer/v1/**`.  
Auth: `Authorization: Bearer {{enforcer_access_token}}` (realm `eden-crm-sec-users`).  
Local/dev (call mobile `:8081` directly): `X-Officer-Sub: enforcer-officer-1`.

### 4.1 Catalog & registration types

`GET /enforcer/v1/catalog` — garages → zones → gates  
`GET /enforcer/v1/registration-types`

**Registration type item**
```json
{ "code": "PRIVATE", "ar": "خصوصي", "en": "Private Cars" }
```

Codes: `PRIVATE`, `COMMERCIAL`, `PUBLIC_TRANSPORT`, `DIPLOMATIC`, `TEMPORARY_CUSTOMS`.

---

### 4.2 Assign garage / zone / gate (required before scan)

`POST /enforcer/v1/assignments`

**Request**
```json
{
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1"
}
```

**Response**
```json
{
  "id": 1,
  "officerId": "…",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1",
  "startedAt": "2026-07-27T09:50:00Z",
  "garageName": "Downtown Garage",
  "zoneName": "Zone A"
}
```

Also: `GET /enforcer/v1/assignments/current`, `GET /enforcer/v1/capacity?garageId=&zoneId=`.

---

### 4.3 Check plate (IN)

`POST /enforcer/v1/gates/check`

**Request**
```json
{
  "mode": "IN",
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1"
}
```

Backend looks up plate in **org-admin**:
- Employee / visitor → `segment: "CORPORATE"`
- Else → `segment: "CONSUMER"`

**Response (corporate Shared auto-admit example)**
```json
{
  "outcome": "AUTO_ENTER_SHARED",
  "title": "Vehicle will enter as Shared",
  "message": "Corporate employee: Jane Doe",
  "informationalOnly": true,
  "requireConfirm": true,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "permissionType": "SHARED",
  "admissionPool": "SHARED",
  "subjectType": "EMPLOYEE",
  "segment": "CORPORATE",
  "organizationId": 42,
  "subjectId": 12,
  "subjectCode": "PU-00012",
  "subjectName": "Jane Doe",
  "sharedFreeSlots": null,
  "consumerFreeSlots": null,
  "zoneId": 10,
  "zoneName": null,
  "sessionId": 77,
  "session": { "id": 77, "status": "ACTIVE", "timerRunning": true },
  "invoice": null,
  "reasonCode": null
}
```

**Response (consumer — spot available, confirm)**
```json
{
  "outcome": "CONFIRM_ENTER_CONSUMER",
  "title": "Spot available for consumers in zone 10",
  "informationalOnly": false,
  "requireConfirm": true,
  "segment": "CONSUMER",
  "subjectType": "NONE",
  "admissionPool": "CONSUMER",
  "permissionType": "NONE",
  "consumerFreeSlots": 5,
  "sharedFreeSlots": 0
}
```

Then call `POST /enforcer/v1/gates/enter` with `admissionPool: "CONSUMER"`.

**Important:** For `AUTO_ENTER_*`, admission already happened — Flutter only shows the title and dismisses. **Do not** call `/enter` again.

---

### 4.4 Enter / Deny (when decision required)

`POST /enforcer/v1/gates/enter`

**Request**
```json
{
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1",
  "admissionPool": "CONSUMER"
}
```

`POST /enforcer/v1/gates/deny` — same body shape (Shared shortfall refuse).

---

### 4.5 Check plate (OUT) + payment-gated exit

`POST /enforcer/v1/gates/check` with `"mode": "OUT"`

**Corporate exit**
```json
{
  "outcome": "CONFIRM_EXIT_PERMITTED",
  "exitEnabled": true,
  "segment": "CORPORATE",
  "title": "Vehicle has permission — exit with no fee"
}
```
→ `POST /enforcer/v1/gates/exit`

**Consumer exit (unpaid)**
```json
{
  "outcome": "EXIT_AWAITING_PAYMENT",
  "exitEnabled": false,
  "segment": "CONSUMER",
  "title": "Awaiting Payment",
  "invoice": {
    "id": 55,
    "sessionId": 77,
    "invoiceNumber": "INV-AB12CD34",
    "status": "PENDING_PAYMENT",
    "total": 25.5,
    "currency": "SAR",
    "lines": []
  },
  "session": {
    "id": 77,
    "status": "AWAITING_PAYMENT",
    "timerRunning": true,
    "invoiceId": 55,
    "invoiceStatus": "PENDING_PAYMENT"
  }
}
```

Consumer app pays → or Enforcer stub:
`POST /enforcer/v1/invoices/{invoiceId}/mark-paid` → always `{ "status": "PAID", "success": true }`

Re-check OUT or pay then:
```json
{ "outcome": "CONFIRM_EXIT_PAID", "exitEnabled": true }
```
→ `POST /enforcer/v1/gates/exit`

---

### 4.6 Activity log

`GET /enforcer/v1/activity?page=0&size=20` — scoped to officer’s assigned garage.

---

## 5. Gate outcomes cheat sheet

| Outcome | Segment | Officer action |
|---------|---------|----------------|
| `AUTO_ENTER_SHARED` | CORPORATE | Info only — already in |
| `AUTO_ENTER_DEDICATED` | CORPORATE | Info only |
| `AUTO_ENTER_ALLOCATED` | CORPORATE | Info only |
| `AUTO_ENTER_CONSUMER` | CONSUMER | Info only |
| `CONFIRM_ENTER_CONSUMER` | CONSUMER | Call `/enter` |
| `SHARED_FULL_CONSUMER_FALLBACK` | CORPORATE | `/enter` as CONSUMER or `/deny` |
| `DENY_ZONE_FULL` | CONSUMER | Show reason (zone named) |
| `CONFIRM_EXIT_PERMITTED` | CORPORATE | Call `/exit` |
| `EXIT_AWAITING_PAYMENT` | CONSUMER | Wait for pay |
| `CONFIRM_EXIT_PAID` | CONSUMER | Call `/exit` |

Demo plates (BRD): `AAA111`–`AAA118`.

Org-admin entitlement map: `SHARED_POOL→SHARED`, `GUARANTEED→DEDICATED`, `ALLOCATED→ALLOCATION`.

---

## 6. Session statuses (consumer timer)

| Status | Timer | Meaning |
|--------|-------|---------|
| `PENDING_ENTRY` | off | Prepared, not at gate yet |
| `ACTIVE` | **on** | Inside garage |
| `AWAITING_PAYMENT` | **on** | Exit checked; pay before release |
| `ENDED` | off | Exited |

---

## 7. Kafka events (fan-out)

| Topic | Events |
|-------|--------|
| `parknova.session.events` | `SESSION_PREPARED`, `SESSION_STARTED`, `INVOICE_CREATED`, `SESSION_ENDED` |
| `parknova.capacity.events` | `SHARED_POOL_CHANGED`, `ZONE_CONSUMER_QUOTA_CHANGED` |
| `parknova.payment.events` | `INVOICE_PAID` |

Disable: `parknova.kafka.enabled=false`.

---

## 8. Postman

Import: [`docs/postman/ParkNova-Consumer-Enforcer-Journey.postman_collection.json`](./postman/ParkNova-Consumer-Enforcer-Journey.postman_collection.json)

**Runner order**
1. Health  
2. Consumer Auth (register → login)  
3. Consumer setup (bootstrap, lookups, vehicle, garages)  
4. Consumer prepare  
5. Enforcer setup (catalog, assign)  
6. Enforcer IN check (+ enter if needed)  
7. Consumer active (timer)  
8. Enforcer OUT check → mark paid → exit  
9. Consumer pay (if needed) / history / logout  

Set `enforcer_access_token` for gateway JWT, or point `baseUrl` to `http://localhost:8081` and use `X-Officer-Sub` for local Enforcer tests without Keycloak officer tokens.

---

## 9. Environment checklist

| Variable | Example |
|----------|---------|
| Gateway | `http://localhost:8080` |
| Mobile | `http://localhost:8081` |
| Offstreet | `http://localhost:8090` |
| Org-admin | `http://localhost:8091` (`ORG_ADMIN_SERVICE_URL`) |
| Kafka | `KAFKA_URL=localhost:9092` |
| Consumer realm | `parknova` |
| Enforcer realm | `eden-crm-sec-users` |
