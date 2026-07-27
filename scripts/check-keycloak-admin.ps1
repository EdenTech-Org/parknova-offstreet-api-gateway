# Diagnoses Keycloak Admin API access for the gateway client (client_credentials).
# Does not print tokens or secrets. Reads KEYCLOAK_* from env or application defaults.

$ErrorActionPreference = "Stop"

$base = if ($env:KEYCLOAK_BASE_URL) { $env:KEYCLOAK_BASE_URL.TrimEnd('/') } else { "https://keycloak-dev.eden-tech.io" }
$realm = if ($env:KEYCLOAK_REALM) { $env:KEYCLOAK_REALM } else { "parknova" }
$clientId = if ($env:KEYCLOAK_CLIENT_ID) { $env:KEYCLOAK_CLIENT_ID } else { "parknova-api-gateway" }
$secret = $env:KEYCLOAK_CLIENT_SECRET
if (-not $secret) {
    Write-Error "Set KEYCLOAK_CLIENT_SECRET to the confidential client secret, then re-run."
}

Write-Host "Checking $base realm=$realm client=$clientId"

$tokenUri = "$base/realms/$realm/protocol/openid-connect/token"
$body = @{
    grant_type    = "client_credentials"
    client_id     = $clientId
    client_secret = $secret
}

try {
    $tokenResp = Invoke-RestMethod -Method Post -Uri $tokenUri -Body $body -ContentType "application/x-www-form-urlencoded"
} catch {
    Write-Host "FAIL: cannot obtain client_credentials token (wrong secret, client, or realm)."
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    exit 1
}

# Decode JWT payload only to list role names (not printed as raw token)
$parts = $tokenResp.access_token.Split('.')
$payloadB64 = $parts[1].Replace('-', '+').Replace('_', '/')
while ($payloadB64.Length % 4) { $payloadB64 += '=' }
$payloadJson = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payloadB64))
$payload = $payloadJson | ConvertFrom-Json

$needed = @('manage-users', 'view-users', 'query-users')
$rmRoles = @()
if ($payload.resource_access -and $payload.resource_access.'realm-management' -and $payload.resource_access.'realm-management'.roles) {
    $rmRoles = @($payload.resource_access.'realm-management'.roles)
}

Write-Host "Service account realm-management roles: $(if ($rmRoles.Count) { $rmRoles -join ', ' } else { '(none)' })"
$missing = @($needed | Where-Object { $_ -notin $rmRoles })
if ($missing.Count) {
    Write-Host "MISSING roles: $($missing -join ', ')"
} else {
    Write-Host "Required roles present on token."
}

$usersUri = "$base/admin/realms/$realm/users?max=1"
$headers = @{ Authorization = "Bearer $($tokenResp.access_token)" }
try {
    $resp = Invoke-WebRequest -Method Get -Uri $usersUri -Headers $headers
    Write-Host "Admin users API: HTTP $($resp.StatusCode) OK - register should work after gateway restart."
    exit 0
} catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Write-Host "Admin users API: HTTP $code"
    if ($code -eq 403) {
        Write-Host ""
        Write-Host "Fix in Keycloak Admin Console:"
        Write-Host "  1. Realm: $realm"
        Write-Host "  2. Clients -> $clientId -> Settings"
        Write-Host "     - Client authentication: ON"
        Write-Host "     - Service accounts roles: ON (Authentication flow / Capability config)"
        Write-Host "  3. Clients -> $clientId -> Service account roles"
        Write-Host "     - Click Assign role -> Filter by clients -> realm-management"
        Write-Host "     - Assign: manage-users, view-users, query-users"
        Write-Host "  4. Re-run this script (roles appear on a NEW token only)"
    }
    exit 1
}
