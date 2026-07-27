# PARKNOVA — Enforcer Check-In / Check-Out via Kafka

How vehicle **entry/exit** moves from the **Enforcer (workforce) Flutter app** through **Kafka** into **offstreet + mobile** services, and how the **consumer Flutter app** reacts — without the consumer app calling ANPR HTTP APIs.

---

## 1. Platform map

| Surface | Repo / system | Role |
|---------|---------------|------|
| **Consumer Flutter app** | mobile client | Register/login, vehicles, map, prepare session, pay invoice, live session UI |
| **Enforcer Flutter app** | workforce client (new) | Officer login, garage/zone/gate assignment, plate capture, In/Out decisions |
| **Org Admin** | `parknova-org-admin` | Corporate employees & visitors, permissions (Shared / Dedicated / Allocation) |
| **API Gateway** | `parknova-offstreet-api-gateway` | Auth (Keycloak), JWT for consumer; routes `/client/**` → mobile |
| **Mobile service** | `parknova-offstreet-mobile-service-backend` | Consumer profiles, vehicles, sessions, invoices, notifications |
| **Offstreet service** | `parknova-offstreet-service` | Garages, zones, capacity quotas, pricing, grants/permissions source of truth |
| **Kafka** | platform bus | Async gate commands + domain events for real-time sync |

```text
                    ┌─────────────────────┐
                    │  Org Admin (web)    │  employees / visitors / permissions
                    └──────────┬──────────┘
                               │ REST (admin APIs)
                               ▼
┌──────────────┐      ┌────────────────────┐      ┌─────────────────────┐
│ Consumer     │ JWT  │  API Gateway       │      │ Enforcer Flutter    │
│ Flutter App  │─────▶│  /auth + /client   │      │ (workforce)         │
└──────▲───────┘      └─────────┬──────────┘      └──────────┬──────────┘
       │                        │                            │ JWT staff
       │ poll / push            │                            │ REST: check plate,
       │ notifications          ▼                            │ confirm enter/exit
       │              ┌────────────────────┐                 │
       │              │ Mobile service     │◀── commands ────┤
       │              │ sessions/invoices  │                 │
       │              └─────────┬──────────┘                 │
       │                        │ Feign / events             │
       │                        ▼                            ▼
       │              ┌────────────────────┐      ┌─────────────────────┐
       └──────────────│ Offstreet service  │◀─────│ Kafka               │
         capacity     │ capacity/pricing/  │      │ gate.* topics       │
         consistency  │ permissions        │      └─────────────────────┘
                      └────────────────────┘
```

**Important change vs today’s Postman ANPR HTTP:**  
Gate officers do **not** hit `POST /internal/v1/sessions/check-in|check-out` from the consumer app.  
The **Enforcer app** drives entry/exit; backends publish/consume **Kafka** so capacity and sessions stay consistent across Consumer, Enforcer, and Admin.

Keep the existing internal HTTP ANPR endpoints as a **compat/test adapter**.

## Implemented (mobile-service)

Enforcer REST lives in `parknova-offstreet-mobile-service-backend` under `/enforcer/v1/**`.
Staff JWT realm: **`eden-crm-sec-users`** (gateway dual JWKS). Kafka JsonSerializer producers publish session/capacity/payment events.
See [ENFORCER-API.md](./ENFORCER-API.md) and Postman `ParkNova-Enforcer-Gate.postman_collection.json`.

Consumer timer: `GET /client/v1/sessions/active` returns `timerRunning`, `elapsedSeconds`, `startedAt`, `serverTime`, and while exit is unpaid status is `AWAITING_PAYMENT` (timer still runs until Enforcer confirms exit after payment).

---

## 2. Responsibility split

### Offstreet service (business logic core)
- Garage / zone / gate catalog
- **Shared pool** + **zone Consumer quota** (time/day aware)
- Permission validation (staff/visitor grants from org-admin)
- Pricing configuration consumed at session start/end
- Authoritative free-spot counters
- Publishes capacity change events

### Mobile service (consumer runtime)
- Consumer identity bootstrap from JWT
- Registered plates (for matching consumer sessions)
- `prepare` session (product selection before arrival)
- Active session / history for consumer app
- Invoice + stub/real payment for **consumer** exits
- In-app notifications (`SESSION_STARTED`, `SESSION_ENDED`, `PAYMENT_REQUIRED`)
- Consumes Kafka **session lifecycle** events (or owns consumer-session writes after offstreet confirms)

### Org Admin
- Provision corporate employees & visitors
- Issue / expire permissions (`Shared`, `Dedicated`, `Allocation`)
- Permissions are read by offstreet (or cached) at gate check time

### Enforcer Flutter app
- Staff Keycloak login
- Assignment: Garage → Zone → Gate
- Plate UI (4 digits + 3 letters) + registration type
- In / Out mode
- Calls **synchronous REST “gate check / confirm”** APIs (fast UX), which **internally** use offstreet + publish Kafka for fan-out
- Does **not** process payments; waits for `paymentStatus=PAID`

### Consumer Flutter app
- Never calls check-in/out
- After `prepare`, listens/polls for session start
- On exit: shows invoice, pays, then session closes
- Availability badges from `GET /client/v1/garages/{id}/availability` (fed by same capacity truth)

---

## 3. Kafka topics (proposed)

| Topic | Producers | Consumers | Purpose |
|-------|-----------|-----------|---------|
| `parknova.gate.commands` | Enforcer BFF / gate API | Offstreet (primary), Mobile (optional mirror) | Intent: CHECK_PLATE, CONFIRM_ENTRY, CONFIRM_EXIT |
| `parknova.gate.results` | Offstreet | Enforcer BFF (or reply via sync REST) | Decision payloads for UI (auto-admit, deny, confirm CTA, invoice) |
| `parknova.session.events` | Mobile and/or Offstreet | Mobile, Consumer push, Admin analytics | SESSION_PREPARED, SESSION_STARTED, SESSION_ENDED, INVOICE_CREATED |
| `parknova.capacity.events` | Offstreet | Mobile, Admin, Enforcer cache | ZONE_CONSUMER_QUOTA_CHANGED, SHARED_POOL_CHANGED |
| `parknova.payment.events` | Billing / mobile payment | Enforcer (enable Exit), Mobile | INVOICE_PAID |

**Pattern:** Enforcer UI stays **request/response (REST)** for &lt;3s gate flow.  
That REST handler runs the BRD rules in **offstreet**, then **publishes Kafka events** so Consumer app + capacity UIs update without coupling.

Alternatively (fully async): Enforcer publishes to `gate.commands` and waits on `gate.results` with correlationId — still Kafka-backed; REST is only a thin wait-wrapper.

Recommended for Flutter UX: **Sync REST → Offstreet decision engine → Kafka fan-out**.

---

## 4. Enforcer → backend API shape (sync)

Base: gateway or dedicated workforce route group (e.g. `/enforcer/v1/**`), JWT with staff roles.

| Step | API | Notes |
|------|-----|--------|
| Login | `POST /auth/login` (staff client) or Keycloak direct | BR-01 |
| Assignment | `POST /enforcer/v1/assignments` `{garageId,zoneId,gateId}` | BR-02–04 |
| Check plate | `POST /enforcer/v1/gates/check` | Returns outcome + UI mode |
| Confirm enter | `POST /enforcer/v1/gates/enter` | When officer must decide |
| Confirm exit | `POST /enforcer/v1/gates/exit` | Disabled until paid for consumers |
| Activity log | `GET /enforcer/v1/activity` | Scoped to garage |

### `POST /enforcer/v1/gates/check` request

```json
{
  "mode": "IN",
  "plateNumber": "1234ABC",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1",
  "recognizedAt": "2026-07-27T10:00:00Z"
}
```

### Response outcomes (map to BR-17a–e)

| `outcome` | Officer UI | Capacity effect |
|-----------|------------|-----------------|
| `AUTO_ENTER_SHARED` | Title: “Vehicle will enter as Shared” — info only | Shared −1 |
| `AUTO_ENTER_DEDICATED` | “Vehicle will enter as Dedicated” | none |
| `AUTO_ENTER_ALLOCATED` | “Vehicle will enter as Allocated” | none |
| `AUTO_ENTER_CONSUMER` | Info only (AAA114 style) | Zone consumer −1, start billed session |
| `CONFIRM_ENTER_SHARED` | Confirm CTA | Shared −1 on confirm |
| `SHARED_FULL_CONSUMER_FALLBACK` | AAA111 message + Enter as Consumer / Deny | Consumer −1 if chosen |
| `DENY_ZONE_FULL` | Name zone; no CTA | none |
| `CONFIRM_EXIT_PERMITTED` | Exit CTA, no fee | Shared +1 on confirm |
| `EXIT_AWAITING_PAYMENT` | Invoice shown; Exit disabled | none until paid |
| `CONFIRM_EXIT_PAID` | Exit enabled | Consumer +1 on confirm |

After each successful confirm, offstreet publishes `capacity.events` + `session.events`.

---

## 5. Kafka event contracts (examples)

### Session started (consumer)

```json
{
  "eventType": "SESSION_STARTED",
  "eventId": "uuid",
  "occurredAt": "2026-07-27T10:00:00Z",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1",
  "plateNumber": "1234ABC",
  "plateCountry": "SA",
  "pool": "CONSUMER",
  "sessionId": 99,
  "consumerUserId": 55,
  "officerId": "staff-sub",
  "correlationId": "gate-check-uuid"
}
```

### Capacity changed

```json
{
  "eventType": "ZONE_CONSUMER_QUOTA_CHANGED",
  "garageId": 1,
  "zoneId": 10,
  "periodKey": "WEEKDAY_DAY",
  "freeSlots": 12,
  "totalSlots": 40,
  "occurredAt": "2026-07-27T10:00:01Z"
}
```

### Invoice awaiting payment (exit)

```json
{
  "eventType": "INVOICE_CREATED",
  "sessionId": 99,
  "invoiceId": 77,
  "amount": 25.50,
  "currency": "SAR",
  "status": "AWAITING_PAYMENT",
  "consumerUserId": 55
}
```

---

## 6. Flutter consumer app — how to handle gate events

Consumer app **never** opens check-in/out screens. Flow:

### Before arrival
1. Login → Bootstrap → Vehicles  
2. Map / garage detail / availability  
3. `POST /client/v1/sessions/prepare` `{garageId, productId, vehicleId}`  
4. Show “Ready for arrival” state

### While parking (entry happened at gate via Enforcer)
1. Subscribe to push (FCM) **or** poll `GET /client/v1/sessions/active` every N seconds / on resume  
2. When `SESSION_STARTED` notification arrives → navigate to **Active Session** timer  
3. Refresh garage availability widgets from API (backed by capacity events)

### Exit / pay
1. Gate officer starts Out mode → backend creates proforma invoice  
2. Consumer receives `PAYMENT_REQUIRED` / `INVOICE_CREATED`  
3. App opens invoice → `POST /client/v1/invoices/{id}/pay`  
4. Payment service emits `INVOICE_PAID` → Enforcer enables “Exit the Vehicle”  
5. On confirmed exit → `SESSION_ENDED` → consumer shows receipt / history

### Suggested Flutter layers

```text
GateEventInbox (optional WebSocket/SSE later)
    │
    ├─ NotificationRepository  ← FCM + local DB
    ├─ SessionCubit            ← polls /active + applies push
    ├─ InvoiceCubit            ← pay + status
    └─ AvailabilityCubit       ← garages/{id}/availability
```

**Polling fallback (MVP):**  
`Timer.periodic` → `GET /client/v1/sessions/active` + `GET /client/v1/notifications` while app is foregrounded.  
Kafka → mobile writes notification row → FCM → app refresh.

---

## 7. End-to-end sequences

### 7.1 Consumer entry (no permission) — zone has quota

```text
Enforcer: check plate (IN)
   → Offstreet: no permission, zone consumer free > 0
   → outcome AUTO_ENTER_CONSUMER or CONFIRM_ENTER
   → on admit: capacity−1, create/start session
   → Kafka: SESSION_STARTED + ZONE_CONSUMER_QUOTA_CHANGED
Mobile: notify consumer, bind plate→user if vehicle registered
Consumer Flutter: show Active Session
```

### 7.2 Staff Shared permission, spots available (AAA112)

```text
Enforcer: check → AUTO_ENTER_SHARED (info only)
Offstreet: Shared−1 (not consumer quota)
Kafka: capacity SHARED_POOL_CHANGED (+ audit)
Consumer app: no billing session (unless also a consumer product — typically none)
```

### 7.3 Shared full → consumer fallback (AAA111)

```text
Enforcer: SHARED_FULL_CONSUMER_FALLBACK
Officer: Enter as Consumer | Deny
If Enter as Consumer → same as 7.1 against zone quota
```

### 7.4 Consumer exit with payment gate (AAA116)

```text
Enforcer: Out check → EXIT_AWAITING_PAYMENT + invoiceId
Kafka: INVOICE_CREATED
Consumer Flutter: pay invoice
Kafka: INVOICE_PAID
Enforcer: Exit enabled → CONFIRM_EXIT
Offstreet/Mobile: end session, Consumer quota+1
Kafka: SESSION_ENDED + capacity event
```

---

## 8. Migration from current ANPR HTTP

| Today | Target |
|-------|--------|
| `POST /internal/v1/sessions/check-in\|out` + `X-SERVICE-TO-SERVICE` | Enforcer REST + Kafka events |
| Consumer Postman calls ANPR directly | Postman folder “Internal ANPR” = **compat/test only** |
| Capacity only in mobile check-in | Offstreet owns pools; mobile reflects via API/events |

**Adapter option:**  
Kafka consumer in mobile listens to `SESSION_STARTED` / `SESSION_ENDED` **or** Enforcer BFF still calls existing `ParkingSessionService.checkInFromAnpr` internally while publishing Kafka — zero double-write if one writer owns the session table.

---

## 9. Implementation backlog (suggested)

1. **Offstreet:** zone consumer quota resolver (time/day) + Shared pool counters + permission lookup API  
2. **Org-admin:** expose/sync permissions used at gate  
3. **Gate API (new module or offstreet):** `/enforcer/v1/gates/check|enter|exit` implementing BR-17a–e  
4. **Kafka:** topics + producers/consumers; capacity fan-out  
5. **Mobile:** consume session/invoice events → notifications + FCM  
6. **Enforcer Flutter:** assignment + plate UI + outcome screens  
7. **Consumer Flutter:** prepare → active via push/poll → pay on exit  
8. **Deprecate** public use of internal ANPR from consumer Postman happy path  

---

## 10. What the consumer Flutter team should implement now

| Screen | Trigger | API / event |
|--------|---------|-------------|
| Ready to park | User action | `POST /sessions/prepare` |
| Active session | Push `SESSION_STARTED` or poll `/sessions/active` | GET active |
| Pay | Push `PAYMENT_REQUIRED` | GET invoice → POST pay |
| Done | Push `SESSION_ENDED` | History |
| Map badge | App resume / timer | GET availability |

Do **not** implement check-in/out buttons in the consumer app — that is **Enforcer-only**.
