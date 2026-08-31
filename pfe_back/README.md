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
- Application onboarding:
  - `POST /api/applications` (public — developers submit a request, no login)
  - `GET /api/applications`, `GET /api/applications/pending`, `GET /api/applications/{id}`
  - `POST /api/applications/{id}/approve` — simulates provisioning, issues an
    Application ID + integration token, marks READY, and **emails the developer**
  - `POST /api/applications/{id}/reject`
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
- Azure sync (required in every mode): `APP_AZURE_TENANT_ID`, `APP_AZURE_SUBSCRIPTION_ID`, `APP_AZURE_RESOURCE_GROUP`
- Azure sync (Docker / CI only): `APP_AZURE_CLIENT_ID`, `APP_AZURE_CLIENT_SECRET`
- Webhook HMAC: `APP_INFRA_WEBHOOK_SECRET`
- Email (onboarding notifications): `APP_MAIL_ENABLED`, `APP_MAIL_FROM`, `APP_PUBLIC_URL`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`

`APP_INFRA_WEBHOOK_SECRET` must match GitHub Actions secret `INFRA_WEBHOOK_HMAC_SECRET`.

## Email Notifications

When an admin **approves** an application, `ApplicationService.approve()` calls
`MailService.send(...)` to email the request's **contact email** the onboarding
details: Application ID, integration token, portal URL, the registered resource
list, and a quick integration guide. The email never contains Azure credentials.

- `MailService` runs `@Async` and **never throws** — a bad SMTP config logs an
  error but does not roll back the approval.
- **Disabled by default** (`APP_MAIL_ENABLED=false`): emails are logged as
  `[MAIL DISABLED] Would send to …` instead of sent — handy for local dev with
  no mail server.
- To actually send, enable SMTP via the repo-root `.env` file (read by
  `docker-compose.yml`). For Gmail, use a 16-char **App Password** (requires
  2-Step Verification), not your normal password:

```dotenv
# .env  (gitignored)
APP_MAIL_ENABLED=true
APP_MAIL_FROM=PFE DevSecOps <you@gmail.com>
APP_PUBLIC_URL=http://localhost:4200
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=you@gmail.com
SPRING_MAIL_PASSWORD=your16charapppass
```

Then `docker compose up -d --build` (or, for a local `mvnw` run, export the same
vars in your shell before `.\mvnw.cmd spring-boot:run`).


## Azure Authentication (DefaultAzureCredential)

The backend authenticates to Azure through `DefaultAzureCredential`, so the
same code runs in every environment. The auth source depends on what env vars
are present at startup:

| Environment | Wins in the chain | What you configure |
|---|---|---|
| Laptop (`mvnw`) | `AzureCliCredential` (uses `az login` cache) | 3 non-secret IDs + `az login` — **no client secret on disk** |
| IDE | `IntelliJCredential` / VS Code plugin | Sign in via the Azure plugin |
| Docker Compose | `EnvironmentCredential` | 4 `APP_AZURE_*` vars via `.env` (including client secret) |
| GitHub Actions | `EnvironmentCredential` | Same 4 vars via `secrets.AZURE_*` |
| Prod on Azure (App Service / AKS / VM) | `ManagedIdentityCredential` | Assign a Managed Identity with Reader — **no secret anywhere** |

If `APP_AZURE_CLIENT_ID` **and** `APP_AZURE_CLIENT_SECRET` are both set, the
code short-circuits to an explicit `ClientSecretCredential` (Docker/CI path).
Otherwise it falls through the chain (laptop/prod path).

Leaving `APP_AZURE_TENANT_ID` or `APP_AZURE_SUBSCRIPTION_ID` empty disables
the Azure integration entirely — the backend still boots (DB-only mode), just
without the real-time infra sync.

## Run Locally

```powershell
# One-time: sign in with your Azure account
az login
az account set --subscription <sub-id>

# Then, in any PowerShell window:
Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_back"
.\mvnw.cmd spring-boot:run
```

At startup you should see:

```
Azure auth: DefaultAzureCredential (az login / MI / IDE), tenant …, subscription …
Resource Graph client ready
```

And after each scheduled sweep (every 30 s):

```
✅ azure_resource table filled: 7 resources upserted, 0 soft-deleted (kind=RG_DELTA, runId=…)
```

## Verify Quickly

```powershell
# login
$body = '{"username":"admin","password":"admin123"}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $body
```

## Exposing the Backend to GitHub Actions (ngrok)

GitHub-hosted runners cannot reach `http://localhost:8080`. To receive the Terraform workflow webhook (`POST /api/infra/sync/trigger`) while running the backend on your laptop, tunnel it through **ngrok**.

### Why

The workflow step in `.github/workflows/terraform.yml` POSTs an HMAC-signed payload to `BACKEND_WEBHOOK_URL`. That URL must be publicly reachable. ngrok gives `localhost:8080` a temporary public HTTPS URL.

### Steps

1. Start the backend on `:8080`:
   ```powershell
   Push-Location "C:\Users\fathi\OneDrive\Desktop\PFE\pfe_back"
   .\mvnw.cmd spring-boot:run
   ```
2. In a second terminal, start ngrok:
   ```powershell
   ngrok http 8080
   ```
   Copy the `https://<random>.ngrok-free.app` forwarding URL.
3. Update the GitHub repo secret `BACKEND_WEBHOOK_URL` to that URL. Either form works — the workflow normalizes both:
   - Base:  `https://<random>.ngrok-free.app`
   - Full:  `https://<random>.ngrok-free.app/api/infra/sync/trigger`
4. Make sure the HMAC secrets match:
   - Backend: `APP_INFRA_WEBHOOK_SECRET` (env or `application.properties` default)
   - GitHub Actions: `INFRA_WEBHOOK_HMAC_SECRET`

   Mismatch -> backend responds `401 invalid HMAC`.
5. Trigger the workflow (`git push` on `infra/**` or manual `workflow_dispatch`). After `terraform apply/destroy` succeeds:
   - Workflow signs the JSON body (`X-Hub-Signature-256: sha256=<hex>`) and POSTs it.
   - ngrok forwards it to your backend.
   - `HmacVerifier` validates the signature.
   - `ResourceSyncService.runFullSync()` runs a Resource Graph sweep and upserts via `ResourceUpsertService`.
   - Updates broadcast on `/topic/resources` and `/topic/vm-status`; the frontend `/infra-live` page flashes rows.

### Gotchas

- **Free ngrok URLs change on every restart** -> re-paste `BACKEND_WEBHOOK_URL` in GitHub each time. Use `ngrok http --domain=<reserved>.ngrok-free.app 8080` with a reserved domain to avoid it.
- **Backend must be running** when the workflow fires. If it isn't, the POST fails; Terraform still applies, and the next scheduled 30-second Resource Graph poll (`APP_INFRA_POLL_RG_SECONDS`) will catch up.
- **Port collision**: don't run `docker compose up` at the same time — both bind `:8080`.
- **Azure creds still required** (`APP_AZURE_*`). Without them the webhook returns 200 but the sweep finds nothing.

## Notes

- Table names are snake_case (`azure_resource`), not class names (`AzureResourceEntity`).
- `DataSeeder` ensures default admin `admin/admin123`.
