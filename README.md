# PFE DevSecOps Platform

Monorepo for a DevSecOps dashboard with real-time Azure infrastructure synchronization.

## Repository Map

- `README.md` (this file): global overview + where to read next.
- `CLAUDE.md`: deep technical conventions and architecture notes for coding agents.
- `docs/README.md`: documentation index.
- `docs/WORKFLOW.md`: exact runtime flow (workflow -> backend -> DB -> websocket).
- `docs/USE_CASE_DIAGRAM.md`: actors and use cases.
- `docs/CLASS_DIAGRAM.md`: class relationships for backend sync engine.
- `docs/SEQUENCE_DIAGRAMS.md`: sequence diagrams for core scenarios.
- `pfe_back/README.md`: Spring Boot backend guide.
- `pfe_front/README.md`: Angular frontend guide.
- `infra/README.md`: Terraform infrastructure guide.
- `infra/SETUP_SUPER.md`: full clean-slate Azure setup.

## High-Level Architecture

1. Terraform creates/updates Azure resources.
2. GitHub Actions workflow (`.github/workflows/terraform.yml`) runs plan/apply.
3. Workflow sends an HMAC-signed webhook to backend: `POST /api/infra/sync/trigger`.
4. Backend runs Azure Resource Graph sync and stores resources in MySQL tables (`azure_resource`, `sync_run`, ...).
5. Backend pushes updates over STOMP/WebSocket (`/topic/resources`, `/topic/vm-status`).
6. Frontend `infra-live` page updates without manual refresh.

## Quick Start

### Backend

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_back"
.\mvnw.cmd spring-boot:run
```

### Frontend

```powershell
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_front"
npm start
```

Open:

- Frontend: `http://localhost:4200`
- Live infra page: `http://localhost:4200/infra-live`

## Required Runtime Inputs (backend)

- MySQL datasource (`SPRING_DATASOURCE_*`) or local defaults in `application.properties`.
- Azure access for real cloud sync:
  - `APP_AZURE_TENANT_ID`
  - `APP_AZURE_CLIENT_ID`
  - `APP_AZURE_CLIENT_SECRET`
  - `APP_AZURE_SUBSCRIPTION_ID`
  - `APP_AZURE_RESOURCE_GROUP` (optional filter)
- Webhook secret:
  - Backend: `APP_INFRA_WEBHOOK_SECRET`
  - GitHub Actions: `INFRA_WEBHOOK_HMAC_SECRET`

These two webhook secret values must be identical.

