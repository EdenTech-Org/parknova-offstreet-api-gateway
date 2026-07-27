# Gateway ↔ Mobile auth hop (root cause)

## Symptom

| Call through gateway | Result |
|----------------------|--------|
| `GET /client/v1/lookups/**`, `GET /client/v1/garages/**` | OK |
| `GET /client/v1/me`, `POST /client/v1/me/bootstrap`, `/client/v1/vehicles` | `401 Missing Authorization bearer token` |

Local often works; cluster fails — same code path, different HTTP client / image.

## Root cause (between the two services)

```text
Client                    API Gateway                         Mobile service
  |                            |                                    |
  |  Authorization: Bearer JWT |                                    |
  |--------------------------->|                                    |
  |                     validates JWT (JWKS)                        |
  |                     SecurityContext = JwtAuthenticationToken    |
  |                            |                                    |
  |                            |  RestClient proxy (JDK HttpClient) |
  |                            |  *** drops Authorization ***       |
  |                            |----------------------------------->|
  |                            |                         CurrentUserProvider
  |                            |                         reads Authorization
  |                            |                         → missing → 401
```

1. **Gateway** validates the consumer JWT and allows the request.
2. **Gateway MVC** proxies with JDK `HttpClient`. That client treats `Authorization` as a restricted header and **does not send it** downstream unless allowlisted.
3. **Mobile** `CurrentUserProvider` needs either:
   - `Authorization: Bearer …`, or
   - gateway identity headers `X-User-Sub` (+ optional username/email/phone).
4. Lookups/garages never call `CurrentUserProvider`, so they succeed without a user.

This is **not** a wrong HTTP method on bootstrap, and not Keycloak rejecting the token (gateway already accepted it).

## Fix (both sides)

### Gateway (`parknova-offstreet-api-gateway`)

1. **`GatewayProxyClientConfig`** — use `SimpleClientHttpRequestFactory` instead of JDK HttpClient so `Authorization` is forwarded.
2. **`ForwardJwtHeadersFilter`** — after JWT validation, always inject:
   - consumer: `X-User-Sub`, `X-User-Username`, `X-User-Email`, `X-User-Phone`
   - enforcer: `X-Officer-Sub`, `X-Officer-Username`, `X-Officer-Email`
   - marker: `X-Parknova-Gateway: 1`
3. Optional hardening: `-Djdk.httpclient.allowRestrictedHeaders=authorization,host` in Docker `ENTRYPOINT`.

### Mobile (`parknova-offstreet-mobile-service-backend`)

1. **`CurrentUserProvider`** — prefer `X-User-Sub` (gateway identity) before parsing Bearer.
2. **`CurrentOfficerProvider`** — prefer `X-Officer-Sub` the same way.

## Deploy

Redeploy **both** images (gateway + mobile). Gateway alone is not enough if mobile still only looks at Bearer after a strip; mobile alone is not enough if gateway never sends `X-User-*`.

## Verify

```http
GET /client/v1/me
Authorization: Bearer <parknova access_token>
```

Expect `200` profile JSON. On mobile logs/debug, request should show `X-User-Sub` and/or `Authorization`.
