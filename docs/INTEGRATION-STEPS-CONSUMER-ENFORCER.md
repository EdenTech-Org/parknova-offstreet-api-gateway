# ParkNova — Step-by-Step Integration Guide  
## Consumer App + Enforcer App (Request / Response JSON)

**Base URL:** `http://localhost:8080` (API Gateway)

| App | Auth | Header |
|-----|------|--------|
| **Consumer** | Keycloak realm `parknova` via `/auth/login` | `Authorization: Bearer {access_token}` |
| **Enforcer** | Keycloak realm `eden-crm-sec-users` | `Authorization: Bearer {enforcer_token}` |

**Important rule:** Consumer app never calls check-in / check-out. Only Enforcer does gate entry/exit. Consumer prepares, shows the timer, and pays.

```text
Consumer Flutter          Enforcer Flutter
      │                         │
      ▼                         ▼
   Gateway :8080
      │ /client/**              │ /enforcer/**
      ▼                         ▼
   Mobile service ──► Org-admin (employee / visitor plate)
                 └──► Offstreet (garage / capacity / price)
```

---

# PART A — Consumer Flutter (step by step)

---

## Step C1 — Register

**When:** First open / sign up screen  
**Method:** `POST /auth/register`  
**Auth:** none

### Request
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

### Response `201`
```json
{
  "message": "User registered successfully"
}
```

### Response `409` (already exists)
```json
{
  "detail": "User already exists",
  "status": 409
}
```

**Integrate:** On success → go to Login. On 409 → go to Login.

---

## Step C2 — Login

**When:** Login screen  
**Method:** `POST /auth/login`  
**Auth:** none

### Request
```json
{
  "email": "jdoe@example.com",
  "password": "SecurePass1!"
}
```

### Response `200`
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  "scope": "openid profile email"
}
```

**Integrate:** Save `access_token` and `refresh_token`. Send `Authorization: Bearer {access_token}` on all `/client/**` calls.

---

## Step C3 — Bootstrap profile

**When:** After login (once per install / session)  
**Method:** `POST /client/v1/me/bootstrap`  
**Auth:** Consumer JWT  
**Body:** empty

### Response `200`
```json
{
  "id": 1,
  "keycloakSub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "jdoe",
  "email": "jdoe@example.com",
  "phone": "+966500000000",
  "fullNameAr": null,
  "fullNameEn": "John Doe",
  "locale": "ar",
  "status": "ACTIVE"
}
```

**Integrate:** Creates consumer DB user from JWT if missing. Call before vehicles/sessions.

---

## Step C4 — Get / update profile (optional)

**Method:** `GET /client/v1/me`

### Response `200`
```json
{
  "id": 1,
  "keycloakSub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "jdoe",
  "email": "jdoe@example.com",
  "phone": "+966500000000",
  "fullNameAr": null,
  "fullNameEn": "John Doe",
  "locale": "ar",
  "status": "ACTIVE"
}
```

**Method:** `PUT /client/v1/me`

### Request
```json
{
  "phone": "+966511111111",
  "locale": "en"
}
```

### Response `200` — same shape as GET `/me`

---

## Step C5 — Vehicle enums (for add-vehicle form)

Same enums as org-admin. Hardcode in Flutter or mirror Enforcer catalog:

### Size
`SMALL` | `NORMAL` | `EXTENDED`

### Registration type
`GET /enforcer/v1/registration-types` (Enforcer JWT) returns the same codes, or use:

| code | en |
|------|-----|
| `PRIVATE_CARS` | Private Cars |
| `COMMERCIAL` | Commercial |
| `PUBLIC_TRANSPORT` | Public Transport |
| `DIPLOMATIC` | Diplomatic |
| `TEMPORARY_ENTRY_CUSTOMS` | Temporary Entry / Customs |

**Integrate:** Dropdowns for `size` and `registrationType` — no lookup IDs.

---

## Step C6 — Create vehicle

**When:** Add vehicle screen  
**Method:** `POST /client/v1/vehicles`

### Request
```json
{
  "plate": "1234-ABC",
  "size": "NORMAL",
  "registrationType": "PRIVATE_CARS"
}
```

### Response `201`
```json
{
  "id": 9,
  "plate": "1234-ABC",
  "size": "NORMAL",
  "registrationType": "PRIVATE_CARS",
  "active": true
}
```

**Integrate:** Save `vehicleId` (`id`). Plate format must be `digits-letters` (same as org-admin). Max 3 vehicles. First vehicle is `active=true`.

Also: `GET /client/v1/vehicles` to list. Activate: `POST /client/v1/vehicles/{id}/activate`.

Update: `PUT /client/v1/vehicles/{id}` with the same body shape as create.

---

## Step C7 — List garages (map)

**Method:** `GET /client/v1/garages?lat=24.7136&lng=46.6753&radiusKm=20&includePeriods=true`

### Response `200` (shape)
```json
{
  "content": [
    {
      "garageId": 1,
      "name": "Downtown Garage",
      "latitude": 24.7136,
      "longitude": 46.6753,
      "workingTimeFrom": "00:00",
      "workingTimeTo": "23:59",
      "country": "SA",
      "timeZone": "Asia/Riyadh",
      "distanceKm": 1.2,
      "fromPrice": 10.0,
      "currency": "SAR",
      "zones": [
        {
          "zoneId": 10,
          "zoneName": "Zone A",
          "zoneCode": "A",
          "businessModelType": "QUOTA",
          "periods": [
            {
              "periodId": 100,
              "dayOfWeek": "MON",
              "consumerEnabled": true,
              "products": [
                {
                  "productId": 5,
                  "productCode": "HOURLY",
                  "productName": "Hourly",
                  "currency": "SAR",
                  "basePrice": 10.0
                }
              ]
            }
          ]
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

**Integrate:** Show map pins. Save `garageId`, `zoneId`, `productId` from selected garage/product.

---

## Step C8 — Garage availability badge

**Method:** `GET /client/v1/garages/1/availability`

### Response `200`
```json
{
  "garageId": 1,
  "evaluatedAt": "2026-07-27T10:00:00Z",
  "available": true,
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

`status`: `AVAILABLE` | `FULL` | `CLOSED`

---

## Step C9 — Prepare session (before arrival)

**When:** User taps “Park here” / select product  
**Method:** `POST /client/v1/sessions/prepare`

### Request
```json
{
  "garageId": 1,
  "productId": 5,
  "vehicleId": 9
}
```

### Response `200`
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

**Integrate:**
1. Save `sessionId` (`id`).
2. Show screen: **“Ready for arrival — go to the gate”**.
3. Start polling Step C10 (or wait for push `SESSION_STARTED`).
4. Do **not** call any check-in API from the consumer app.

---

## Step C10 — Active session / live timer

**When:** After Enforcer admits the vehicle at the gate  
**Method:** `GET /client/v1/sessions/active`  
**Poll:** every 3–5 seconds while on Active screen (or on app resume / FCM)

### Response `200` (parked — timer ON)
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
  "registrationType": "PRIVATE",
  "admissionPool": "CONSUMER",
  "permissionType": "NONE",
  "entryGateId": "ENTRY-1",
  "exitGateId": null,
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

### Response `200` (exit pending payment — timer still ON)
```json
{
  "id": 77,
  "garageId": 1,
  "zoneId": 10,
  "vehicleId": 9,
  "productId": 5,
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "status": "AWAITING_PAYMENT",
  "admissionPool": "CONSUMER",
  "permissionType": "NONE",
  "entryGateId": "ENTRY-1",
  "exitGateId": null,
  "startedAt": "2026-07-27T10:00:00Z",
  "endedAt": null,
  "elapsedSeconds": 7200,
  "timerRunning": true,
  "estimatedCost": 25.5,
  "currency": "SAR",
  "invoiceId": 55,
  "invoiceStatus": "PENDING_PAYMENT",
  "serverTime": "2026-07-27T12:00:00Z"
}
```

### Response `404`
```json
{
  "detail": "No active session",
  "status": 404
}
```

**Integrate (timer):**
```text
if (timerRunning == true) {
  showElapsed = duration(now synced to serverTime - startedAt)
  // or use elapsedSeconds and tick locally
}
if (status == AWAITING_PAYMENT && invoiceId != null) {
  navigate to Pay screen
}
```

---

## Step C11 — Get invoice

**Method:** `GET /client/v1/invoices/55`

### Response `200`
```json
{
  "id": 55,
  "sessionId": 77,
  "invoiceNumber": "INV-AB12CD34",
  "status": "PENDING_PAYMENT",
  "subtotal": 20.0,
  "serviceFee": 1.6,
  "vatRate": 15.0,
  "vatAmount": 3.24,
  "total": 24.84,
  "currency": "SAR",
  "garageNameAr": "مرآب وسط المدينة",
  "garageNameEn": "Downtown Garage",
  "lines": [
    {
      "lineType": "PARKING",
      "descriptionAr": "رسوم المواقف",
      "descriptionEn": "Parking charges",
      "quantity": 1.0,
      "unitPrice": 20.0,
      "lineTotal": 20.0
    },
    {
      "lineType": "SERVICE_FEE",
      "descriptionAr": "رسوم الخدمة",
      "descriptionEn": "Service fee",
      "quantity": 1.0,
      "unitPrice": 1.6,
      "lineTotal": 1.6
    },
    {
      "lineType": "VAT",
      "descriptionAr": "ضريبة 15%",
      "descriptionEn": "VAT 15%",
      "quantity": 1.0,
      "unitPrice": 3.24,
      "lineTotal": 3.24
    }
  ]
}
```

---

## Step C12 — Pay invoice (always success)

**Method:** `POST /client/v1/invoices/55/pay`

### Request
```json
{
  "paymentMethod": "CARD"
}
```

### Response `200` (always)
```json
{
  "paymentId": 9,
  "status": "PAID",
  "invoiceStatus": "PAID",
  "providerRef": "STUB-CARD-a1b2c3d4"
}
```

**Integrate:** Show “Paid”. Enforcer Exit button becomes enabled. Keep polling active until session ends (`404` on active).

---

## Step C13 — Notifications (optional)

**Method:** `GET /client/v1/notifications?page=0&size=20`

### Response `200`
```json
{
  "content": [
    {
      "id": 3,
      "type": "SESSION_STARTED",
      "titleAr": "بدأت جلسة الموقف",
      "titleEn": "Parking session started",
      "bodyAr": "تم تسجيل دخول مركبتك ABC1234",
      "bodyEn": "Your vehicle ABC1234 checked in — timer running",
      "channel": "IN_APP",
      "status": "UNREAD",
      "createdAt": "2026-07-27T10:00:01Z",
      "readAt": null
    },
    {
      "id": 4,
      "type": "PAYMENT_REQUIRED",
      "titleAr": "يرجى سداد رسوم الموقف",
      "titleEn": "Parking payment required",
      "bodyAr": "فاتورة مبدئية جاهزة للسداد قبل الخروج",
      "bodyEn": "A proforma invoice is ready — pay before exit",
      "channel": "IN_APP",
      "status": "UNREAD",
      "createdAt": "2026-07-27T12:00:01Z",
      "readAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2
}
```

Types: `SESSION_STARTED` | `PAYMENT_REQUIRED` | `SESSION_ENDED`

Mark read: `POST /client/v1/notifications/3/read`

---

## Step C14 — History

**Method:** `GET /client/v1/sessions?page=0&size=20`

### Response `200`
```json
{
  "content": [
    {
      "id": 77,
      "garageId": 1,
      "zoneId": 10,
      "vehicleId": 9,
      "productId": 5,
      "plateNumber": "ABC1234",
      "status": "ENDED",
      "startedAt": "2026-07-27T10:00:00Z",
      "endedAt": "2026-07-27T12:05:00Z",
      "elapsedSeconds": 7500,
      "timerRunning": false,
      "estimatedCost": 24.84,
      "currency": "SAR",
      "invoiceId": 55,
      "invoiceStatus": "PAID",
      "serverTime": "2026-07-27T12:06:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

---

## Step C15 — Logout

**Method:** `POST /auth/logout`

### Request
```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Response `200`
```json
{
  "message": "Logged out"
}
```

**Integrate:** Clear local tokens and navigate to login.

---

# PART B — Enforcer Flutter (step by step)

---

## Step E0 — Officer login

Login against Keycloak realm **`eden-crm-sec-users`** (not `/auth/login` on the gateway — that is consumer realm).

Obtain `enforcer_token`, then use:

```http
Authorization: Bearer {enforcer_token}
```

Local test without Keycloak officer token (call mobile `:8081` directly):

```http
X-Officer-Sub: enforcer-officer-1
```

---

## Step E1 — Load catalog (garage → zone → gate)

**Method:** `GET /enforcer/v1/catalog`

### Response `200`
```json
[
  {
    "garageId": 1,
    "name": "Downtown Garage",
    "zones": [
      {
        "zoneId": 10,
        "zoneName": "Zone A",
        "zoneCode": "A",
        "gates": [
          { "gateId": "ENTRY-1", "label": "Entry 1" },
          { "gateId": "EXIT-1", "label": "Exit 1" },
          { "gateId": "GATE-1", "label": "Gate 1" }
        ]
      }
    ]
  }
]
```

**Integrate:** Cascading dropdowns Garage → Zone → Gate. Block scan until all three selected and confirmed (Step E3).

---

## Step E2 — Registration types

**Method:** `GET /enforcer/v1/registration-types`

### Response `200`
```json
[
  { "code": "PRIVATE", "ar": "خصوصي", "en": "Private Cars" },
  { "code": "COMMERCIAL", "ar": "نقل خاص", "en": "Commercial" },
  { "code": "PUBLIC_TRANSPORT", "ar": "نقل عام", "en": "Public Transport" },
  { "code": "DIPLOMATIC", "ar": "هيئة دبلوماسية", "en": "Diplomatic" },
  { "code": "TEMPORARY_CUSTOMS", "ar": "الإدخال المؤقت / الجمارك", "en": "Temporary Entry / Customs" }
]
```

**Integrate:** Single dropdown; default `PRIVATE`. Show `ar — en`.

---

## Step E3 — Assign garage / zone / gate

**Method:** `POST /enforcer/v1/assignments`

### Request
```json
{
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1"
}
```

### Response `200`
```json
{
  "id": 1,
  "officerId": "a1b2c3d4-officer-sub",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1",
  "startedAt": "2026-07-27T09:50:00Z",
  "garageName": "Downtown Garage",
  "zoneName": "Zone A"
}
```

**Also:** `GET /enforcer/v1/assignments/current` — same response shape.

**Integrate:** Show assignment on home/scan screen. All later gate calls must use the same `garageId` / `zoneId` / `gateId`.

---

## Step E4 — Capacity (optional UI)

**Method:** `GET /enforcer/v1/capacity?garageId=1&zoneId=10`

### Response `200`
```json
{
  "sharedFree": 8,
  "sharedTotal": 20,
  "consumerFree": 12,
  "consumerTotal": 40,
  "periodKey": "WEEKDAY_DAY",
  "zoneId": 10
}
```

---

## Step E5 — Check plate IN (entry)

**When:** Officer enters/scans plate + registration type, mode = Entry  
**Method:** `POST /enforcer/v1/gates/check`

### Request
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

Backend checks **org-admin**: employee / visitor → `segment=CORPORATE`, else `CONSUMER`.

---

### Response example — Corporate employee (Shared auto)

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
  "sessionId": 88,
  "session": {
    "id": 88,
    "garageId": 1,
    "zoneId": 10,
    "plateNumber": "ABC1234",
    "status": "ACTIVE",
    "admissionPool": "SHARED",
    "permissionType": "SHARED",
    "entryGateId": "ENTRY-1",
    "startedAt": "2026-07-27T10:00:00Z",
    "elapsedSeconds": 0,
    "timerRunning": true,
    "estimatedCost": 0.0,
    "currency": "SAR",
    "serverTime": "2026-07-27T10:00:00Z"
  },
  "invoice": null,
  "reasonCode": null
}
```

**Integrate:** Show **title only** (no capacity numbers). Button label `Enter the Vehicle` = dismiss only. **Do not** call `/gates/enter` again (already admitted).

Same pattern for:
- `AUTO_ENTER_DEDICATED` — title `Vehicle will enter as Dedicated`
- `AUTO_ENTER_ALLOCATED` — title `Vehicle will enter as Allocated`
- `AUTO_ENTER_CONSUMER` — title `Vehicle will enter as Consumer`

---

### Response example — Consumer confirm

```json
{
  "outcome": "CONFIRM_ENTER_CONSUMER",
  "title": "Spot available for consumers in zone 10",
  "message": null,
  "informationalOnly": false,
  "requireConfirm": true,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "permissionType": "NONE",
  "admissionPool": "CONSUMER",
  "subjectType": "NONE",
  "segment": "CONSUMER",
  "organizationId": null,
  "subjectId": null,
  "subjectCode": null,
  "subjectName": null,
  "sharedFreeSlots": 0,
  "consumerFreeSlots": 5,
  "zoneId": 10,
  "zoneName": "Zone 10",
  "sessionId": null,
  "session": null,
  "invoice": null,
  "reasonCode": null
}
```

**Integrate:** Officer taps **Enter the Vehicle** → call Step E6 with `admissionPool: "CONSUMER"`.

---

### Response example — Shared full → consumer fallback

```json
{
  "outcome": "SHARED_FULL_CONSUMER_FALLBACK",
  "title": "Permission available with type Shared but No available parking spot",
  "message": null,
  "informationalOnly": false,
  "requireConfirm": false,
  "exitEnabled": false,
  "consumerFallbackEnabled": true,
  "permissionType": "SHARED",
  "admissionPool": "SHARED",
  "subjectType": "EMPLOYEE",
  "segment": "CORPORATE",
  "subjectName": "Jane Doe",
  "sharedFreeSlots": 0,
  "consumerFreeSlots": 5,
  "zoneId": 10,
  "reasonCode": "SHARED_FULL"
}
```

**Integrate:**
- If `consumerFallbackEnabled == true` → button **Enter as Consumer** → Step E6 with `admissionPool: "CONSUMER"`
- Button **Deny Entry** → Step E7

---

### Response example — Zone full (deny)

```json
{
  "outcome": "DENY_ZONE_FULL",
  "title": "No free spots in zone 10",
  "message": "Consumer quota for the checked-in zone has no free spots.",
  "informationalOnly": false,
  "requireConfirm": false,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "permissionType": "NONE",
  "admissionPool": "CONSUMER",
  "subjectType": "NONE",
  "segment": "CONSUMER",
  "consumerFreeSlots": 0,
  "zoneId": 10,
  "zoneName": "Zone 10",
  "reasonCode": "ZONE_FULL"
}
```

**Integrate:** Show message; no Enter button.

---

## Step E6 — Confirm enter

**Method:** `POST /enforcer/v1/gates/enter`  
**Only when** outcome is `CONFIRM_ENTER_CONSUMER` or fallback Enter-as-Consumer.

### Request
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

### Response `200`
```json
{
  "outcome": "ENTERED",
  "title": "Vehicle will enter as Consumer",
  "message": null,
  "informationalOnly": true,
  "requireConfirm": false,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "permissionType": "NONE",
  "admissionPool": "CONSUMER",
  "subjectType": "NONE",
  "segment": "CONSUMER",
  "sessionId": 77,
  "session": {
    "id": 77,
    "status": "ACTIVE",
    "timerRunning": true,
    "startedAt": "2026-07-27T10:00:00Z",
    "plateNumber": "ABC1234",
    "entryGateId": "ENTRY-1"
  },
  "invoice": null,
  "reasonCode": null
}
```

After this, Consumer app Step C10 starts showing the timer.

---

## Step E7 — Deny entry

**Method:** `POST /enforcer/v1/gates/deny`

### Request
```json
{
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1"
}
```

### Response `200`
```json
{
  "outcome": "DENIED",
  "title": "Entry denied",
  "informationalOnly": true,
  "requireConfirm": false,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "reasonCode": "OFFICER_DENY"
}
```

---

## Step E8 — Check plate OUT (exit)

**Method:** `POST /enforcer/v1/gates/check`  
Switch UI to **Exit mode**; `gateId` may be `EXIT-1`.

### Request
```json
{
  "mode": "OUT",
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "EXIT-1"
}
```

---

### Response — Corporate exit (no fee)

```json
{
  "outcome": "CONFIRM_EXIT_PERMITTED",
  "title": "Vehicle has permission — exit with no fee",
  "message": "Corporate employee: Jane Doe",
  "informationalOnly": false,
  "requireConfirm": true,
  "exitEnabled": true,
  "consumerFallbackEnabled": false,
  "permissionType": "SHARED",
  "admissionPool": "SHARED",
  "subjectType": "EMPLOYEE",
  "segment": "CORPORATE",
  "subjectName": "Jane Doe",
  "sessionId": 88,
  "session": {
    "id": 88,
    "status": "ACTIVE",
    "timerRunning": true,
    "admissionPool": "SHARED",
    "permissionType": "SHARED"
  },
  "invoice": null,
  "reasonCode": null
}
```

**Integrate:** Enable **Exit the Vehicle** → Step E11.

---

### Response — Consumer exit (awaiting payment)

```json
{
  "outcome": "EXIT_AWAITING_PAYMENT",
  "title": "Awaiting Payment",
  "message": "Exit the Vehicle stays disabled until the proforma invoice is paid.",
  "informationalOnly": false,
  "requireConfirm": false,
  "exitEnabled": false,
  "consumerFallbackEnabled": false,
  "permissionType": "NONE",
  "admissionPool": "CONSUMER",
  "subjectType": "NONE",
  "segment": "CONSUMER",
  "sessionId": 77,
  "session": {
    "id": 77,
    "status": "AWAITING_PAYMENT",
    "timerRunning": true,
    "invoiceId": 55,
    "invoiceStatus": "PENDING_PAYMENT",
    "startedAt": "2026-07-27T10:00:00Z",
    "elapsedSeconds": 7200,
    "estimatedCost": 24.84,
    "currency": "SAR"
  },
  "invoice": {
    "id": 55,
    "sessionId": 77,
    "invoiceNumber": "INV-AB12CD34",
    "status": "PENDING_PAYMENT",
    "subtotal": 20.0,
    "serviceFee": 1.6,
    "vatRate": 15.0,
    "vatAmount": 3.24,
    "total": 24.84,
    "currency": "SAR",
    "garageNameAr": "مرآب وسط المدينة",
    "garageNameEn": "Downtown Garage",
    "lines": []
  },
  "reasonCode": "PAYMENT_REQUIRED"
}
```

**Integrate:**
1. Show invoice amount.
2. Keep **Exit the Vehicle** disabled (`exitEnabled: false`).
3. Consumer pays (Step C12) **or** call Step E9 stub.
4. Re-check OUT (Step E8) or go to Step E10 when paid.

---

## Step E9 — Mark invoice paid (stub / kiosk)

**Method:** `POST /enforcer/v1/invoices/55/mark-paid`  
**Body:** empty

### Response `200` (always)
```json
{
  "invoiceId": 55,
  "status": "PAID",
  "success": true
}
```

---

## Step E10 — Check OUT after payment

Same request as Step E8.

### Response
```json
{
  "outcome": "CONFIRM_EXIT_PAID",
  "title": "Payment confirmed — exit enabled",
  "exitEnabled": true,
  "segment": "CONSUMER",
  "invoice": {
    "id": 55,
    "status": "PAID",
    "total": 24.84,
    "currency": "SAR"
  },
  "session": {
    "id": 77,
    "status": "AWAITING_PAYMENT",
    "invoiceId": 55,
    "invoiceStatus": "PAID",
    "timerRunning": true
  }
}
```

**Integrate:** Enable **Exit the Vehicle** → Step E11.

---

## Step E11 — Confirm exit

**Method:** `POST /enforcer/v1/gates/exit`

### Request
```json
{
  "plateNumber": "ABC1234",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "EXIT-1",
  "invoiceId": 55
}
```

### Response `200`
```json
{
  "outcome": "EXITED",
  "title": "Vehicle exited",
  "informationalOnly": true,
  "requireConfirm": false,
  "exitEnabled": false,
  "permissionType": "NONE",
  "admissionPool": "CONSUMER",
  "segment": "CONSUMER",
  "sessionId": 77,
  "session": {
    "id": 77,
    "status": "ENDED",
    "timerRunning": false,
    "startedAt": "2026-07-27T10:00:00Z",
    "endedAt": "2026-07-27T12:05:00Z",
    "exitGateId": "EXIT-1",
    "invoiceId": 55,
    "invoiceStatus": "PAID"
  },
  "invoice": {
    "id": 55,
    "status": "PAID",
    "total": 24.84,
    "currency": "SAR"
  }
}
```

Consumer active session becomes `404`; history shows `ENDED`.

---

## Step E12 — Activity log

**Method:** `GET /enforcer/v1/activity?page=0&size=20`

### Response `200`
```json
{
  "content": [
    {
      "id": 10,
      "action": "EXIT",
      "outcome": "EXITED",
      "plateNumber": "ABC1234",
      "registrationType": "PRIVATE",
      "garageId": 1,
      "zoneId": 10,
      "gateId": "EXIT-1",
      "sessionId": 77,
      "reasonCode": null,
      "details": null,
      "createdAt": "2026-07-27T12:05:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

---

# PART C — Flutter integration checklist

## Consumer app screens → APIs

| Screen | Steps | APIs |
|--------|-------|------|
| Register | C1 | `POST /auth/register` |
| Login | C2 | `POST /auth/login` |
| Splash / home init | C3 | `POST /client/v1/me/bootstrap` |
| Add vehicle | C5–C6 | lookups + `POST /vehicles` |
| Map | C7–C8 | garages + availability |
| Ready to park | C9 | `POST /sessions/prepare` |
| Active timer | C10 | poll `GET /sessions/active` |
| Pay | C11–C12 | get invoice + `POST .../pay` |
| Inbox | C13 | notifications |
| History | C14 | `GET /sessions` |
| Logout | C15 | `POST /auth/logout` |

## Enforcer app screens → APIs

| Screen | Steps | APIs |
|--------|-------|------|
| Login | E0 | Keycloak `eden-crm-sec-users` |
| Assignment | E1–E3 | catalog + assignments |
| Scan home | E2, E4 | registration types + capacity |
| Entry result | E5–E7 | check / enter / deny |
| Exit result | E8–E11 | check / mark-paid / exit |
| Activity | E12 | activity |

## Decision tree after `gates/check` (IN)

```text
outcome
├── AUTO_ENTER_*          → show title → dismiss (already entered)
├── CONFIRM_ENTER_CONSUMER → Enter → POST /gates/enter
├── SHARED_FULL_...       → Enter as Consumer OR Deny
└── DENY_ZONE_FULL        → show reason only
```

## Decision tree after `gates/check` (OUT)

```text
outcome
├── CONFIRM_EXIT_PERMITTED → Exit → POST /gates/exit
├── EXIT_AWAITING_PAYMENT  → wait pay → re-check or CONFIRM_EXIT_PAID
├── CONFIRM_EXIT_PAID      → Exit → POST /gates/exit
└── NOT_ENTERED            → show error
```

---

# PART D — Ordered happy path (one parking visit)

| # | Actor | Step | API |
|---|-------|------|-----|
| 1 | Consumer | C1–C2 | register + login |
| 2 | Consumer | C3–C6 | bootstrap + vehicle |
| 3 | Consumer | C7–C9 | garage + prepare |
| 4 | Enforcer | E0–E3 | login + assign |
| 5 | Enforcer | E5 (+E6 if needed) | check IN / enter |
| 6 | Consumer | C10 | active timer |
| 7 | Enforcer | E8 | check OUT |
| 8 | Consumer | C12 | pay (or Enforcer E9) |
| 9 | Enforcer | E11 | exit |
| 10 | Consumer | C14–C15 | history + logout |

---

# PART E — Postman

Import: [`postman/ParkNova-Consumer-Enforcer-Journey.postman_collection.json`](./postman/ParkNova-Consumer-Enforcer-Journey.postman_collection.json)

Folders **A → I** match this journey. Set `access_token` (consumer) and `enforcer_access_token` (officer), or for Enforcer local tests set `baseUrl=http://localhost:8081` and use `X-Officer-Sub`.
