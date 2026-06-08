# Backend (`pfe_back`)

Spring Boot 4 REST backend for authentication, DevSecOps domain APIs, and real-time Azure infrastructure synchronization.

## Stack

- Java 17
- Spring Boot 4.0.6
- Spring Data JPA + MySQL
- Spring Security + JWT
- STOMP/WebSocket (`/ws`)

## Main Responsibilities

- Auth (`/api/auth/login`, `/api/auth/register`, `/api/auth/me`)
- Domain APIs (images, pipelines, alerts, deployments, audit)
- Live infra APIs:
  - `GET /api/infra/resources`
  - `GET /api/infra/resources/{id}`
  - `GET /api/infra/resources/{id}/history`
  - `GET /api/infra/sync/runs`
  - `POST /api/infra/sync/trigger` (HMAC-protected)

## Real-time Infra Flow

1. Trigger from GitHub Actions webhook (`/api/infra/sync/trigger`) or scheduler.
2. `ResourceSyncService.runFullSync()` calls Azure Resource Graph.
3. `ResourceUpsertService.upsert()` writes to MySQL tables:
   - `azure_resource`
   - `azure_resource_history`
   - `vm_state_event`
   - `sync_run`
4. Backend broadcasts changes to:
   - `/topic/resources`
   - `/topic/vm-status`

## Configuration

Source: `pfe_back/src/main/resources/application.properties`

Important env vars:

- DB: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- JWT: `APP_JWT_SECRET`, `APP_JWT_EXPIRATION_MS`
- Azure sync: `APP_AZURE_TENANT_ID`, `APP_AZURE_CLIENT_ID`, `APP_AZURE_CLIENT_SECRET`, `APP_AZURE_SUBSCRIPTION_ID`, `APP_AZURE_RESOURCE_GROUP`
- Webhook HMAC: `APP_INFRA_WEBHOOK_SECRET`

`APP_INFRA_WEBHOOK_SECRET` must match GitHub Actions secret `INFRA_WEBHOOK_HMAC_SECRET`.

## Run Locally

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_back"
.\mvnw.cmd spring-boot:run
```

## Verify Quickly

```powershell
# login
$body = '{"username":"admin","password":"admin123"}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body
```

## Notes

- Table names are snake_case (`azure_resource`), not class names (`AzureResourceEntity`).
- `DataSeeder` ensures default admin `admin/admin123`.

