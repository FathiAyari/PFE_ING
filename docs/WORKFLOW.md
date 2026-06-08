# Workflow Guide

This document explains how infrastructure changes flow from Git push to live UI updates.

## End-to-End Flow (Arrows)

```text
git push (infra/** or workflow file)
    -> GitHub Actions: .github/workflows/terraform.yml
    -> terraform init / validate / plan / apply
    -> terraform output -json (tf-outputs.json)
    -> HMAC sign payload with INFRA_WEBHOOK_HMAC_SECRET
    -> POST BACKEND_WEBHOOK_URL[/api/infra/sync/trigger]
    -> Backend: InfraSyncController verifies X-Hub-Signature-256
    -> ResourceSyncService.runFullSync(Kind.TERRAFORM_HOOK)
    -> AzureResourceGraphClient.listAllResources()
    -> ResourceUpsertService.upsert(...)
    -> MySQL: azure_resource + azure_resource_history + vm_state_event + sync_run
    -> STOMP broadcast: /topic/resources and /topic/vm-status
    -> Angular /infra-live updates in real time
```

## Workflow File Breakdown

Source: `.github/workflows/terraform.yml`

- **Triggers**
  - `push` to `main` for `infra/**`
  - `pull_request` for plan
  - `workflow_dispatch` for plan/apply/destroy
- **Core stages**
  1. Checkout
  2. Setup Terraform
  3. `terraform init` (Azure Storage backend)
  4. `terraform validate`
  5. `terraform plan` (PR/manual plan)
  6. `terraform apply` (push to main/manual apply)
  7. `terraform destroy` (manual destroy)
  8. Export outputs artifact
  9. Notify backend webhook

## Webhook Security

- Workflow computes HMAC-SHA256 over JSON body.
- Header sent: `X-Hub-Signature-256: sha256=<hex>`.
- Backend verifies with `app.infra.webhook-secret`.

### Must match

- GitHub secret: `INFRA_WEBHOOK_HMAC_SECRET`
- Backend runtime value: `APP_INFRA_WEBHOOK_SECRET` (or default from `application.properties`)

If values differ -> webhook returns `401 invalid HMAC`.

## URL Behavior

`BACKEND_WEBHOOK_URL` may be either:

- base URL: `https://<host>`
- full URL: `https://<host>/api/infra/sync/trigger`

Workflow normalizes to the full endpoint before POST.

## Runtime Requirements

- Backend reachable from GitHub Actions (public URL, usually ngrok for local testing).
- Backend running with valid Azure credentials:
  - `APP_AZURE_TENANT_ID`
  - `APP_AZURE_CLIENT_ID`
  - `APP_AZURE_CLIENT_SECRET`
  - `APP_AZURE_SUBSCRIPTION_ID`
  - `APP_AZURE_RESOURCE_GROUP` (optional filter)

Without Azure credentials, webhook can succeed but sync stores no real Azure resources.

## Workflow Designs (Under the Hood)

This section visualises the runtime moving parts behind every feature shipped in this repo. All diagrams are Mermaid — render them in any Markdown viewer that supports it (GitHub, VS Code, IntelliJ).

### 1. High-Level Architecture

```mermaid
flowchart LR
    Dev[Developer] -->|git push infra/**| GH[GitHub Actions<br/>terraform.yml]
    GH -->|terraform apply| AZ[(Azure<br/>Resource Group)]
    GH -->|HMAC POST<br/>/api/infra/sync/trigger| BE
    AZ -->|Event Grid<br/>POST /api/azure/events| BE
    BE[Spring Boot<br/>pfe_back] -->|Resource Graph<br/>Kusto query| AZ
    BE -->|JPA| DB[(MySQL 8.4<br/>azure_resource<br/>azure_resource_history<br/>sync_run)]
    BE -->|STOMP<br/>/topic/resources<br/>/topic/vm-status| FE[Angular SPA<br/>pfe_front]
    User[Cloud Admin] -->|HTTPS| FE
    FE -->|REST + JWT| BE
```

### 2. Three Ingestion Paths → One Upsert Pipeline

Every change — wherever it originates — funnels through `ResourceUpsertService.upsert(...)`, the **only** writer to `azure_resource` / `azure_resource_history` and the **only** STOMP publisher.

```mermaid
flowchart TB
    subgraph Sources [Ingestion Sources]
        A[Scheduled Poll<br/>every 30s]
        B[Terraform Webhook<br/>HMAC-signed]
        C[Event Grid<br/>VM state changes]
    end
    A --> RSS[ResourceSyncService<br/>runFullSync]
    B --> RSS
    C --> EGC[AzureEventsController<br/>dedupe via processed_event]
    RSS --> RGC[AzureResourceGraphClient<br/>Kusto sweep + Retryable]
    RGC --> RUS
    EGC --> RUS[ResourceUpsertService.upsert]
    RUS -->|diff JSON| HIST[(azure_resource_history<br/>CREATE / UPDATE / DELETE<br/>STATE_CHANGE / TAG_CHANGE)]
    RUS -->|insert/update/soft-delete| RES[(azure_resource)]
    RUS -->|broadcast| WS[STOMP broker<br/>/topic/resources<br/>/topic/vm-status]
    WS --> UI[Angular /infra-live<br/>row flash + live table]
```

### 3. Terraform → Live UI (Sequence)

```mermaid
sequenceDiagram
    autonumber
    participant Dev as Developer
    participant GH as GitHub Actions
    participant TF as Terraform CLI
    participant Azure as Azure RM
    participant BE as Spring Boot
    participant ARG as Azure Resource Graph
    participant DB as MySQL
    participant WS as STOMP broker
    participant UI as Angular SPA

    Dev->>GH: git push (infra/**)
    GH->>TF: init / validate / plan / apply
    TF->>Azure: create/update/destroy resources
    GH->>GH: terraform output -json + HMAC sign
    GH->>BE: POST /api/infra/sync/trigger<br/>X-Hub-Signature-256
    BE->>BE: HmacVerifier (constant-time)
    BE->>ARG: Kusto query — full inventory
    ARG-->>BE: resources JSON
    BE->>DB: upsert + append history + soft-delete missing
    BE->>WS: publish ResourceChange events
    WS-->>UI: STOMP frame (/topic/resources)
    UI->>UI: flash row, update table
```

### 4. Periodic Drift Detection (Scheduled Poll)

Runs even when nothing else fires — guarantees the DB converges with Azure within `app.infra.poll.rg-seconds`.

```mermaid
sequenceDiagram
    autonumber
    participant SCH as @Scheduled (30s)
    participant RSS as ResourceSyncService
    participant ARG as Azure Resource Graph
    participant RUS as ResourceUpsertService
    participant DB as MySQL
    participant WS as STOMP

    SCH->>RSS: runFullSync(SCHEDULED)
    RSS->>DB: insert sync_run (RUNNING)
    RSS->>ARG: list all resources
    ARG-->>RSS: snapshot
    loop each resource
        RSS->>RUS: upsert(snapshot row)
        RUS->>DB: diff vs current, write history
        RUS->>WS: /topic/resources
    end
    RSS->>DB: soft-delete rows missing from snapshot
    RSS->>WS: /topic/resources (DELETE)
    RSS->>DB: update sync_run (SUCCESS, counts)
```

### 5. Azure Event Grid Webhook

```mermaid
sequenceDiagram
    autonumber
    participant EG as Azure Event Grid
    participant BE as AzureEventsController
    participant DB as MySQL
    participant RUS as ResourceUpsertService
    participant WS as STOMP

    EG->>BE: POST /api/azure/events (validation)
    BE-->>EG: validationResponse (handshake)
    EG->>BE: POST /api/azure/events (notification)
    BE->>DB: SELECT processed_event WHERE event_id=?
    alt already processed
        BE-->>EG: 200 OK (no-op)
    else new event
        BE->>DB: INSERT processed_event
        BE->>RUS: upsert(targeted resource)
        RUS->>DB: write history (STATE_CHANGE)
        RUS->>WS: /topic/vm-status
        BE-->>EG: 200 OK
    end
```

### 6. Authentication & Authorisation

```mermaid
sequenceDiagram
    autonumber
    participant UI as Angular SPA
    participant API as /api/auth/*
    participant SVC as AuthService
    participant DB as MySQL
    participant JWT as JwtService

    UI->>API: POST /api/auth/login {username, password}
    API->>SVC: authenticate
    SVC->>DB: findByUsername
    SVC->>SVC: BCrypt.matches
    SVC->>JWT: sign HS512 (24h)
    JWT-->>SVC: token
    SVC-->>UI: AuthResponse {token, expiresAt, user}
    UI->>UI: persist localStorage(pfe-token, pfe-user)
    Note over UI,API: Subsequent requests
    UI->>API: GET /api/* + Authorization: Bearer <token>
    API->>JWT: JwtAuthFilter validates signature + expiry
    alt valid
        API-->>UI: 200 + payload
    else invalid/missing
        API-->>UI: 401/403
        UI->>UI: authInterceptor → logout() → /login
    end
```

### 7. SAFE/UNSAFE Deployment Decision

```mermaid
flowchart LR
    A[POST /api/deployments/images/:id] --> B{DeploymentService.deploy}
    B -->|image.status = SAFE| C[Create Deployment row]
    C --> D[AuditLogService.log<br/>result=ALLOWED]
    D --> E[200 OK]
    B -->|image.status = UNSAFE| F[Reject — throw]
    F --> G[AuditLogService.log<br/>result=DENIED]
    G --> H[400/409 with reason]
```

### 8. Frontend Realtime Wiring

```mermaid
flowchart LR
    BOOT[main.ts<br/>bootstrapApplication] --> CFG[app.config.ts<br/>provideRouter + HttpClient + authInterceptor]
    CFG --> ROUTES[app.routes.ts<br/>guestGuard / authGuard]
    ROUTES --> SHELL[ShellComponent<br/>sidebar + topbar + theme toggle]
    SHELL --> PAGES[Lazy pages<br/>dashboard / images / pipelines<br/>alerts / infra-live / admin]
    PAGES -->|REST| API[api.service.ts<br/>infra.service.ts]
    PAGES -->|subscribe| RT[realtime.service.ts<br/>@stomp/rx-stomp + SockJS]
    RT -->|CONNECT + JWT header| BE[(Spring WS /ws)]
    BE -->|/topic/resources<br/>/topic/vm-status| RT
    RT --> PAGES
```

### 9. Docker Stack Topology

```mermaid
flowchart LR
    Browser[Browser :4200] --> NGINX
    subgraph pfe-network
        NGINX[pfe-frontend<br/>nginx :80]
        BACK[pfe-backend<br/>Spring Boot :8080]
        SQL[(pfe-mysql<br/>MySQL 8.4 :3306)]
    end
    NGINX -->|/api/*| BACK
    NGINX -->|/ws upgrade| BACK
    BACK -->|JDBC| SQL
    BACK -.->|optional| AZ[(Azure)]
    GH[GitHub Actions] -->|HMAC webhook| BACK
```

## Where to Edit Each Piece

| Concern | File |
|---|---|
| GitHub workflow | `.github/workflows/terraform.yml` |
| Terraform resources | `infra/main.tf`, `infra/variables.tf` |
| Webhook verification | `com.pfe.back.infra.util.HmacVerifier`, `InfraSyncController` |
| Single-writer upsert | `com.pfe.back.infra.service.ResourceUpsertService` |
| Scheduled poll | `com.pfe.back.infra.service.ResourceSyncService` (`@Scheduled`) |
| Event Grid handshake | `com.pfe.back.controller.AzureEventsController` |
| STOMP config | `com.pfe.back.config.WebSocketConfig` |
| Frontend live feed | `src/app/core/realtime.service.ts`, `src/app/pages/infra-live/` |
| Auth pipeline | `SecurityConfig`, `JwtAuthFilter`, `auth.interceptor.ts`, `auth.guard.ts` |
