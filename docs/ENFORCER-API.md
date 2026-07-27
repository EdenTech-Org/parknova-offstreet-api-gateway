# Enforcer APIs (mobile-service)

## Corporate vs consumer (org-admin)

Enforcer resolves plate ownership via org-admin:

`GET {ORG_ADMIN}/api/v1/permissions/by-plate?plate=&garageId=&at=`

| Result | Meaning |
|--------|---------|
| `segment=CORPORATE`, `subjectType=EMPLOYEE` | Active permanent-user vehicle + entitlement |
| `segment=CORPORATE`, `subjectType=VISITOR` | Active visitor booking for today/time window |
| `segment=CONSUMER` | No corporate match → consumer quota / billing |

Entitlement mapping: `SHARED_POOL→SHARED`, `GUARANTEED→DEDICATED`, `ALLOCATED→ALLOCATION`.

Mobile config: `parknova.org-admin.base-url` (default `http://localhost:8091`).

---

Base path: `/enforcer/v1/**`  
Auth: Bearer JWT from Keycloak realm **`eden-crm-sec-users`** (gateway validates via JWKS; mobile decodes `sub`).  
Dev headers: `X-Officer-Sub`, `X-Officer-Username`.

Gateway routes `/enforcer/**` → mobile service (`:8081`).

Kafka (producer):
```
spring.kafka.producer.bootstrap-servers=${KAFKA_URL:localhost:9092}
spring.kafka.producer.key-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
```
Topics: `parknova.session.events`, `parknova.capacity.events`, `parknova.payment.events`  
Disable: `parknova.kafka.enabled=false`

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/enforcer/v1/catalog` | Garages → zones → gates for assignment dropdowns |
| GET | `/enforcer/v1/registration-types` | 5 Saudi registration types (ar/en) |
| POST | `/enforcer/v1/assignments` | `{garageId,zoneId,gateId}` — required before scan |
| GET | `/enforcer/v1/assignments/current` | Active assignment |
| GET | `/enforcer/v1/capacity?garageId=&zoneId=` | Shared + zone consumer free/total |
| POST | `/enforcer/v1/gates/check` | Plate check (IN/OUT) — BR outcomes |
| POST | `/enforcer/v1/gates/enter` | Confirm enter (or Enter as Consumer) |
| POST | `/enforcer/v1/gates/exit` | Confirm exit (consumer requires PAID invoice) |
| POST | `/enforcer/v1/gates/deny` | Officer deny (Shared fallback) |
| POST | `/enforcer/v1/invoices/{id}/mark-paid` | Gate/kiosk stub pay → enables Exit |
| GET | `/enforcer/v1/activity` | Officer activity for assigned garage |

### Check body
```json
{
  "mode": "IN",
  "plateNumber": "AAA112",
  "plateCountry": "SA",
  "registrationType": "PRIVATE",
  "garageId": 1,
  "zoneId": 10,
  "gateId": "ENTRY-1"
}
```

### Outcomes
- `AUTO_ENTER_*` — already admitted; Flutter shows title only, **do not** call `/enter` again (ack dismiss only).
- `CONFIRM_ENTER_CONSUMER` / `SHARED_FULL_CONSUMER_FALLBACK` — call `/enter` with `admissionPool=CONSUMER` (or deny).
- `EXIT_AWAITING_PAYMENT` — show invoice; Exit disabled until paid.
- `CONFIRM_EXIT_PAID` / `CONFIRM_EXIT_PERMITTED` — call `/exit`.

Demo plates: AAA111–AAA118 (BRD).

## Consumer Flutter timer

After gate check-in:
1. Poll `GET /client/v1/sessions/active` (or FCM `SESSION_STARTED`).
2. Use `startedAt`, `elapsedSeconds`, `timerRunning`, `serverTime` to drive the timer.
3. Status `ACTIVE` or `AWAITING_PAYMENT` — timer keeps running.
4. On `AWAITING_PAYMENT` / notification `PAYMENT_REQUIRED` → open invoice → `POST /client/v1/invoices/{id}/pay`.
5. Enforcer then enables Exit; after confirm → `SESSION_ENDED`; timer stops (`timerRunning=false`).

Postman: `parknova-offstreet-mobile-service-backend/docs/postman/ParkNova-Enforcer-Gate.postman_collection.json`
