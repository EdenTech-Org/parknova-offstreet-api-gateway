# -*- coding: utf-8 -*-
"""Generate ParkNova Consumer + Enforcer journey Postman collection."""
import json
from pathlib import Path

BASE = "{{baseUrl}}"


def hdr_json():
    return [{"key": "Content-Type", "value": "application/json"}]


def hdr_consumer():
    return [
        {"key": "Authorization", "value": "Bearer {{access_token}}"},
        {"key": "Content-Type", "value": "application/json"},
    ]


def hdr_consumer_get():
    return [{"key": "Authorization", "value": "Bearer {{access_token}}"}]


def hdr_enforcer():
    return [
        {"key": "Authorization", "value": "Bearer {{enforcer_access_token}}"},
        {"key": "X-Officer-Sub", "value": "{{officerSub}}"},
        {"key": "Content-Type", "value": "application/json"},
    ]


def hdr_enforcer_get():
    return [
        {"key": "Authorization", "value": "Bearer {{enforcer_access_token}}"},
        {"key": "X-Officer-Sub", "value": "{{officerSub}}"},
    ]


def req(name, method, path, headers=None, body=None, description="", tests=None):
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": headers or [],
            "url": f"{BASE}{path}",
            "description": description,
        },
        "response": [],
    }
    if body is not None:
        item["request"]["body"] = {"mode": "raw", "raw": body}
    if tests:
        item["event"] = [
            {
                "listen": "test",
                "script": {"type": "text/javascript", "exec": tests},
            }
        ]
    return item


def folder(name, items, description=""):
    f = {"name": name, "item": items}
    if description:
        f["description"] = description
    return f


collection = {
    "info": {
        "_postman_id": "parknova-consumer-enforcer-journey",
        "name": "ParkNova Consumer + Enforcer Journey",
        "description": (
            "Full dual-app E2E through the API Gateway.\n\n"
            "## Realms\n"
            "- Consumer: Keycloak `parknova` via /auth/login → access_token\n"
            "- Enforcer: Keycloak `eden-crm-sec-users` → set enforcer_access_token\n"
            "  (or set baseUrl=http://localhost:8081 and use X-Officer-Sub)\n\n"
            "## Runner order\n"
            "A Health → B Consumer Auth → C Consumer Setup → D Prepare → "
            "E Enforcer Setup → F Entry → G Consumer Timer → H Exit → I Wrap-up\n\n"
            "See docs/CONSUMER-ENFORCER-INTEGRATION-JOURNEY.md"
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080"},
        {"key": "username", "value": "jdoe"},
        {"key": "email", "value": "jdoe@example.com"},
        {"key": "firstName", "value": "John"},
        {"key": "lastName", "value": "Doe"},
        {"key": "phone", "value": "+966500000000"},
        {"key": "password", "value": "SecurePass1!"},
        {"key": "access_token", "value": ""},
        {"key": "refresh_token", "value": ""},
        {"key": "enforcer_access_token", "value": ""},
        {"key": "officerSub", "value": "enforcer-officer-1"},
        {"key": "lat", "value": "24.7136"},
        {"key": "lng", "value": "46.6753"},
        {"key": "garageId", "value": "1"},
        {"key": "zoneId", "value": "10"},
        {"key": "gateId", "value": "ENTRY-1"},
        {"key": "exitGateId", "value": "EXIT-1"},
        {"key": "productId", "value": "1"},
        {"key": "vehicleTypeId", "value": "1"},
        {"key": "modelTypeId", "value": "1"},
        {"key": "licenseTypeId", "value": "1"},
        {"key": "vehicleId", "value": "1"},
        {"key": "sessionId", "value": "1"},
        {"key": "invoiceId", "value": "1"},
        {"key": "plateNumber", "value": "ABC1234"},
        {"key": "plateCountry", "value": "SA"},
        {"key": "registrationType", "value": "PRIVATE"},
    ],
    "item": [],
}

# A. Health
collection["item"].append(
    folder(
        "A. Health",
        [
            req("A.1 Gateway health", "GET", "/actuator/health", description="Gateway up"),
        ],
        "Sanity check",
    )
)

# B. Consumer Auth
collection["item"].append(
    folder(
        "B. Consumer Auth",
        [
            req(
                "B.1 Register",
                "POST",
                "/auth/register",
                hdr_json(),
                '{\n  "username": "{{username}}",\n  "email": "{{email}}",\n  "firstName": "{{firstName}}",\n  "lastName": "{{lastName}}",\n  "phone": "{{phone}}",\n  "password": "{{password}}"\n}',
                "Create consumer in Keycloak realm parknova",
                [
                    "pm.test('Created or exists', function () {",
                    "  pm.expect([201, 409]).to.include(pm.response.code);",
                    "});",
                ],
            ),
            req(
                "B.2 Login",
                "POST",
                "/auth/login",
                hdr_json(),
                '{\n  "email": "{{email}}",\n  "password": "{{password}}"\n}',
                "Email login — stores consumer JWT",
                [
                    "pm.test('Login 200', function () { pm.response.to.have.status(200); });",
                    "const j = pm.response.json();",
                    "pm.collectionVariables.set('access_token', j.access_token);",
                    "pm.collectionVariables.set('refresh_token', j.refresh_token);",
                ],
            ),
        ],
        "Consumer JWT (realm parknova)",
    )
)

# C. Consumer Setup
collection["item"].append(
    folder(
        "C. Consumer Setup",
        [
            req("C.1 Bootstrap", "POST", "/client/v1/me/bootstrap", hdr_consumer_get()),
            req("C.2 Me", "GET", "/client/v1/me", hdr_consumer_get()),
            req(
                "C.3 Vehicle types",
                "GET",
                "/client/v1/lookups/vehicle-types",
                hdr_consumer_get(),
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const a = pm.response.json();",
                    "  const list = Array.isArray(a) ? a : (a.content || a.items || []);",
                    "  if (list[0] && list[0].id) pm.collectionVariables.set('vehicleTypeId', String(list[0].id));",
                    "}",
                ],
            ),
            req(
                "C.4 Model types",
                "GET",
                "/client/v1/lookups/model-types",
                hdr_consumer_get(),
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const a = pm.response.json();",
                    "  const list = Array.isArray(a) ? a : (a.content || a.items || []);",
                    "  if (list[0] && list[0].id) pm.collectionVariables.set('modelTypeId', String(list[0].id));",
                    "}",
                ],
            ),
            req(
                "C.5 License types",
                "GET",
                "/client/v1/lookups/license-types",
                hdr_consumer_get(),
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const a = pm.response.json();",
                    "  const list = Array.isArray(a) ? a : (a.content || a.items || []);",
                    "  if (list[0] && list[0].id) pm.collectionVariables.set('licenseTypeId', String(list[0].id));",
                    "}",
                ],
            ),
            req(
                "C.6 Create vehicle",
                "POST",
                "/client/v1/vehicles",
                hdr_consumer(),
                '{\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "vehicleTypeId": {{vehicleTypeId}},\n  "modelTypeId": {{modelTypeId}},\n  "licenseTypeId": {{licenseTypeId}},\n  "isElectric": false,\n  "make": "Toyota",\n  "model": "Camry",\n  "color": "White",\n  "setAsDefault": true\n}',
                tests=[
                    "if (pm.response.code === 200 || pm.response.code === 201) {",
                    "  pm.collectionVariables.set('vehicleId', String(pm.response.json().id));",
                    "}",
                ],
            ),
            req(
                "C.7 List garages",
                "GET",
                "/client/v1/garages?lat={{lat}}&lng={{lng}}&radiusKm=20&includePeriods=true",
                hdr_consumer_get(),
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const j = pm.response.json();",
                    "  const list = j.content || j.items || (Array.isArray(j) ? j : []);",
                    "  if (list[0]) {",
                    "    const g = list[0];",
                    "    if (g.garageId) pm.collectionVariables.set('garageId', String(g.garageId));",
                    "    if (g.zones && g.zones[0]) {",
                    "      pm.collectionVariables.set('zoneId', String(g.zones[0].zoneId));",
                    "      const p = (g.zones[0].periods || [])[0];",
                    "      if (p && p.products && p.products[0]) {",
                    "        pm.collectionVariables.set('productId', String(p.products[0].productId));",
                    "      }",
                    "    }",
                    "  }",
                    "}",
                ],
            ),
            req(
                "C.8 Garage availability",
                "GET",
                "/client/v1/garages/{{garageId}}/availability",
                hdr_consumer_get(),
            ),
        ],
        "Profile, lookups, vehicle, garages",
    )
)

# D. Prepare
collection["item"].append(
    folder(
        "D. Consumer Prepare",
        [
            req(
                "D.1 Prepare session",
                "POST",
                "/client/v1/sessions/prepare",
                hdr_consumer(),
                '{\n  "garageId": {{garageId}},\n  "productId": {{productId}},\n  "vehicleId": {{vehicleId}}\n}',
                "PENDING_ENTRY — wait for Enforcer gate",
                [
                    "pm.test('Prepare ok', function () {",
                    "  pm.expect([200, 201]).to.include(pm.response.code);",
                    "});",
                    "if (pm.response.code === 200 || pm.response.code === 201) {",
                    "  pm.collectionVariables.set('sessionId', String(pm.response.json().id));",
                    "}",
                ],
            ),
        ],
    )
)

# E. Enforcer setup
collection["item"].append(
    folder(
        "E. Enforcer Setup",
        [
            req(
                "E.1 Catalog",
                "GET",
                "/enforcer/v1/catalog",
                hdr_enforcer_get(),
                description="Garages/zones/gates. Needs enforcer JWT or X-Officer-Sub on :8081",
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const list = pm.response.json();",
                    "  if (Array.isArray(list) && list[0]) {",
                    "    pm.collectionVariables.set('garageId', String(list[0].garageId));",
                    "    if (list[0].zones && list[0].zones[0]) {",
                    "      pm.collectionVariables.set('zoneId', String(list[0].zones[0].zoneId));",
                    "      const gates = list[0].zones[0].gates || [];",
                    "      if (gates[0] && gates[0].gateId) pm.collectionVariables.set('gateId', gates[0].gateId);",
                    "    }",
                    "  }",
                    "}",
                ],
            ),
            req("E.2 Registration types", "GET", "/enforcer/v1/registration-types", hdr_enforcer_get()),
            req(
                "E.3 Assign garage/zone/gate",
                "POST",
                "/enforcer/v1/assignments",
                hdr_enforcer(),
                '{\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{gateId}}"\n}',
            ),
            req("E.4 Current assignment", "GET", "/enforcer/v1/assignments/current", hdr_enforcer_get()),
            req(
                "E.5 Capacity",
                "GET",
                "/enforcer/v1/capacity?garageId={{garageId}}&zoneId={{zoneId}}",
                hdr_enforcer_get(),
            ),
        ],
        "Officer assignment before scanning",
    )
)

# F. Entry
collection["item"].append(
    folder(
        "F. Enforcer Entry (IN)",
        [
            req(
                "F.1 Check IN",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "IN",\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "registrationType": "{{registrationType}}",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{gateId}}"\n}',
                "Resolves CORPORATE (org-admin employee/visitor) vs CONSUMER",
                [
                    "pm.test('Check IN responded', function () {",
                    "  pm.expect([200, 201, 409]).to.include(pm.response.code);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "  const j = pm.response.json();",
                    "  pm.collectionVariables.set('lastOutcome', j.outcome || '');",
                    "  if (j.sessionId) pm.collectionVariables.set('sessionId', String(j.sessionId));",
                    "  if (j.session && j.session.id) pm.collectionVariables.set('sessionId', String(j.session.id));",
                    "  console.log('outcome=' + j.outcome + ' segment=' + j.segment + ' subjectType=' + j.subjectType);",
                    "}",
                ],
            ),
            req(
                "F.2 Enter (only if CONFIRM / fallback)",
                "POST",
                "/enforcer/v1/gates/enter",
                hdr_enforcer(),
                '{\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "registrationType": "{{registrationType}}",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{gateId}}",\n  "admissionPool": "CONSUMER"\n}',
                "Skip for AUTO_ENTER_* (already admitted). Use for CONFIRM_ENTER_CONSUMER or Enter-as-Consumer.",
                [
                    "if (pm.response.code === 200) {",
                    "  const j = pm.response.json();",
                    "  if (j.sessionId) pm.collectionVariables.set('sessionId', String(j.sessionId));",
                    "}",
                ],
            ),
        ],
        "Plate check-in at gate",
    )
)

# G. Consumer timer
collection["item"].append(
    folder(
        "G. Consumer Timer (Active)",
        [
            req(
                "G.1 Active session",
                "GET",
                "/client/v1/sessions/active",
                hdr_consumer_get(),
                description="Poll for timer: timerRunning, elapsedSeconds, startedAt, serverTime",
                tests=[
                    "pm.test('Active or awaiting payment', function () {",
                    "  if (pm.response.code === 200) {",
                    "    const j = pm.response.json();",
                    "    pm.expect(['ACTIVE', 'AWAITING_PAYMENT']).to.include(j.status);",
                    "    pm.collectionVariables.set('sessionId', String(j.id));",
                    "    if (j.invoiceId) pm.collectionVariables.set('invoiceId', String(j.invoiceId));",
                    "  }",
                    "});",
                ],
            ),
            req("G.2 Notifications", "GET", "/client/v1/notifications?page=0&size=20", hdr_consumer_get()),
        ],
        "Consumer app shows live parking timer after gate entry",
    )
)

# H. Exit
collection["item"].append(
    folder(
        "H. Enforcer Exit (OUT) + Pay",
        [
            req(
                "H.1 Check OUT",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "OUT",\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "registrationType": "{{registrationType}}",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{exitGateId}}"\n}',
                "Consumer → EXIT_AWAITING_PAYMENT + invoice; Corporate → CONFIRM_EXIT_PERMITTED",
                [
                    "if (pm.response.code === 200) {",
                    "  const j = pm.response.json();",
                    "  if (j.invoice && j.invoice.id) pm.collectionVariables.set('invoiceId', String(j.invoice.id));",
                    "  if (j.session && j.session.invoiceId) pm.collectionVariables.set('invoiceId', String(j.session.invoiceId));",
                    "  console.log('exit outcome=' + j.outcome + ' exitEnabled=' + j.exitEnabled);",
                    "}",
                ],
            ),
            req(
                "H.2 Consumer pay invoice (always PAID)",
                "POST",
                "/client/v1/invoices/{{invoiceId}}/pay",
                hdr_consumer(),
                '{\n  "paymentMethod": "CARD"\n}',
                "Stub always returns success",
                [
                    "pm.test('Pay success', function () {",
                    "  if (pm.response.code === 200) {",
                    "    pm.expect(pm.response.json().status).to.eql('PAID');",
                    "  }",
                    "});",
                ],
            ),
            req(
                "H.3 Enforcer mark-paid (alt stub)",
                "POST",
                "/enforcer/v1/invoices/{{invoiceId}}/mark-paid",
                hdr_enforcer_get(),
                description="Alternative to consumer pay — always success",
            ),
            req(
                "H.4 Check OUT again (after pay)",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "OUT",\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "registrationType": "{{registrationType}}",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{exitGateId}}"\n}',
                "Expect CONFIRM_EXIT_PAID or CONFIRM_EXIT_PERMITTED",
            ),
            req(
                "H.5 Exit confirm",
                "POST",
                "/enforcer/v1/gates/exit",
                hdr_enforcer(),
                '{\n  "plateNumber": "{{plateNumber}}",\n  "plateCountry": "{{plateCountry}}",\n  "registrationType": "{{registrationType}}",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{exitGateId}}",\n  "invoiceId": {{invoiceId}}\n}',
            ),
            req("H.6 Activity log", "GET", "/enforcer/v1/activity?page=0&size=20", hdr_enforcer_get()),
        ],
        "Payment-gated consumer exit or free corporate exit",
    )
)

# I. Wrap-up
collection["item"].append(
    folder(
        "I. Consumer Wrap-up",
        [
            req("I.1 Session history", "GET", "/client/v1/sessions?page=0&size=20", hdr_consumer_get()),
            req("I.2 List invoices", "GET", "/client/v1/invoices?page=0&size=20", hdr_consumer_get()),
            req(
                "I.3 Logout",
                "POST",
                "/auth/logout",
                hdr_json(),
                '{\n  "refreshToken": "{{refresh_token}}"\n}',
            ),
        ],
    )
)

# J. Demo plates (optional)
collection["item"].append(
    folder(
        "J. Demo plates (optional)",
        [
            req(
                "J.1 Check IN AAA112 (Shared auto)",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "IN",\n  "plateNumber": "AAA112",\n  "plateCountry": "SA",\n  "registrationType": "PRIVATE",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{gateId}}"\n}',
            ),
            req(
                "J.2 Check IN AAA113 (Dedicated)",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "IN",\n  "plateNumber": "AAA113",\n  "plateCountry": "SA",\n  "registrationType": "PRIVATE",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{gateId}}"\n}',
            ),
            req(
                "J.3 Check OUT AAA116 (consumer exit demo)",
                "POST",
                "/enforcer/v1/gates/check",
                hdr_enforcer(),
                '{\n  "mode": "OUT",\n  "plateNumber": "AAA116",\n  "plateCountry": "SA",\n  "registrationType": "PRIVATE",\n  "garageId": {{garageId}},\n  "zoneId": {{zoneId}},\n  "gateId": "{{exitGateId}}"\n}',
                tests=[
                    "if (pm.response.code === 200) {",
                    "  const j = pm.response.json();",
                    "  if (j.invoice && j.invoice.id) pm.collectionVariables.set('invoiceId', String(j.invoice.id));",
                    "}",
                ],
            ),
        ],
        "BRD demo plates AAA111–AAA118 (run after Enforcer assign)",
    )
)

out = Path(r"d:\project\parknova-offstreet-api-gateway\docs\postman\ParkNova-Consumer-Enforcer-Journey.postman_collection.json")
out.write_text(json.dumps(collection, indent=2), encoding="utf-8")
print(f"Wrote {out} ({out.stat().st_size} bytes)")
