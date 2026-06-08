# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Architecture

Two-project monorepo with **no root build**. Treat each as its own project root:

- `pfe_front/` — Angular 19 SPA: **DevSecOps dashboard** (standalone components, **Tailwind CSS v4**, dark/light theme toggle)
- `pfe_back/` — Spring Boot 4.0.6 REST API (Java 17, Maven) backing the dashboard
- `docker-compose.yml` — orchestrates **MySQL 8.4 + backend + frontend (nginx)** on a shared network for a one-command stack.

Backend exposes `/api/**`. In Docker, the frontend nginx proxies `/api/*` to the backend container (so the SPA uses the **relative** `/api` URL and there's no CORS). In local dev, the SPA points at `http://localhost:8080/api` directly and the backend's `SecurityConfig` allows CORS from `http://localhost:4200`.

## Domain (DevSecOps)

Manages **Docker images classified SAFE or UNSAFE**:

- **SAFE** images can be deployed (`POST /api/deployments/images/{id}`).
- **UNSAFE** images are **read-only** — deployment is rejected at the service layer (`DeploymentService.deploy`) and the attempt is recorded in the audit log with `result=DENIED`.
- All deployment attempts (allowed and denied) flow through `AuditLogService.log(...)`.

## Backend: `pfe_back`

- **Stack**: Spring Boot **4.0.6**, **Java 17**, Maven wrapper (`mvnw.cmd` on Windows).
- **Coordinates**: groupId `com.pfe`, base package `com.pfe.back`, main class `com.pfe.back.PfeBackApplication`.
- **Starters**: `data-jpa`, `security`, `validation`, **`webmvc`** (Boot 4 renamed `-web` → `-webmvc`; test starters mirror this), `devtools`, `lombok`. Both **H2** and **MySQL** drivers are on the classpath.
- **DB**: defaults to **MySQL** at `localhost:3306/devsecops` (with `createDatabaseIfNotExist=true`). The H2 block is kept commented in `application.properties` for quick swaps. In Docker, the backend points at the `mysql` service on the compose network via `SPRING_DATASOURCE_URL`.
- **Configuration** (`application.properties`): every value uses `${ENV_VAR:default}` placeholder syntax so env vars can override it. **Env vars are the single source of truth in Docker** — compose injects:
  - `SPRING_DATASOURCE_URL` (points at `mysql:3306`)
  - `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_JPA_HIBERNATE_DDL_AUTO`
  - `APP_JWT_SECRET` (≥32 bytes; defaults to a committed random 48-byte base64 dev secret if unset)
  - `APP_JWT_EXPIRATION_MS`
  - **Azure / real-time infra sync** (all optional — leave blank for DB-only mode):
    - `APP_AZURE_TENANT_ID`, `APP_AZURE_CLIENT_ID`, `APP_AZURE_CLIENT_SECRET`, `APP_AZURE_SUBSCRIPTION_ID`, `APP_AZURE_RESOURCE_GROUP`
    - `APP_INFRA_POLL_ENABLED` (default `true`), `APP_INFRA_POLL_RG_SECONDS` (default `30`)
    - `APP_INFRA_WEBHOOK_SECRET` (must match the GitHub repo secret `INFRA_WEBHOOK_HMAC_SECRET`)
- **Seed data**: `com.pfe.back.config.DataSeeder` (`CommandLineRunner`) populates 7 images, 6 pipeline runs, 5 alerts, 7 system nodes, and seed audit entries on first start. Idempotent (skips if `images.count() > 0`).
- **Security**: `com.pfe.back.config.SecurityConfig` — **stateless JWT auth**. CSRF off, sessions off, CORS to `http://localhost:4200`. `/api/auth/login` and `/api/auth/register` are public; everything else under `/api/**` requires `Authorization: Bearer <token>`. The H2 console (`/h2-console/**`) is open in dev.
- **Auth module**:
  - Single role: **`CLOUD_ADMIN`** (enum `com.pfe.back.entity.Role`).
  - Tokens are HMAC-SHA-512 JWTs signed with `app.jwt.secret` (default 24-hour TTL via `app.jwt.expiration-ms`). See `com.pfe.back.security.JwtService` and `JwtAuthFilter`.
  - Passwords are BCrypt-hashed. `User` entity has `username`, `passwordHash`, `email`, `role`, `enabled`, `createdAt`.
  - `AuthService.login/register/me` + `AuthController` (`/api/auth/{login,register,me}`). Login/register return `AuthResponse { token, expiresAt, user }`.
  - **Default seeded admin**: `admin / admin123` (created by `DataSeeder` on every boot if not present).
- **Lombok**: enabled and excluded from the repackaged jar via `spring-boot-maven-plugin > configuration > excludes`. Preserve that block when editing `pom.xml`.
- **Dockerfile** (`pfe_back/Dockerfile`): multi-stage — `maven:3.9-eclipse-temurin-17` builds the jar, `eclipse-temurin:17-jre-alpine` runs it as a non-root `app` user. Exposes `8080`. Configurable via `JAVA_OPTS`.

### Package layout (established)

- `com.pfe.back.entity` — JPA entities: `DockerImage`, `PipelineRun`, `SecurityAlert`, `SystemNode`, `Deployment`, `AuditLog`, **`User`**; enums `ImageStatus`, `PipelineStatus`, `AlertSeverity`, **`Role` (`CLOUD_ADMIN`)**.
- `com.pfe.back.repository` — `JpaRepository<T, ID>` (e.g. `DockerImageRepository.findByStatusOrderByScannedAtDesc`, `UserRepository.findByUsername`).
- `com.pfe.back.security` — `JwtService` (HMAC-SHA-512), `JwtAuthFilter` (`OncePerRequestFilter` registered before `UsernamePasswordAuthenticationFilter`).
- `com.pfe.back.service` — `@Service` + `@RequiredArgsConstructor` (`DeploymentService`, `DashboardService`, `AuditLogService`, **`AuthService`**).
- `com.pfe.back.controller` — `@RestController` under `/api/...`. Existing endpoints:
  - `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me`
  - `GET /api/dashboard/stats`
  - `GET /api/images`, `/api/images/safe`, `/api/images/unsafe`, `/api/images/{id}`
  - `GET /api/pipelines`
  - `GET /api/alerts`, `/api/alerts/open`, `POST /api/alerts/{id}/acknowledge?actor=...`
  - `GET /api/infrastructure/nodes`, `/api/infrastructure/health`
  - `GET /api/deployments`, `POST /api/deployments/images/{imageId}` (body: `DeployRequest`)
  - `GET /api/audit/logs`
  - **Real-time Azure infra sync** (see "Real-time Azure infra sync" below):
    - `GET /api/infra/resources?type=&rg=&state=&q=&page=&size=` — paginated search
    - `GET /api/infra/resources/{id}`, `GET /api/infra/resources/{id}/history`
    - `GET /api/infra/sync/runs`
    - `POST /api/infra/sync/trigger` (HMAC-protected, called by GitHub Actions)
    - `POST /api/azure/events` (Azure Event Grid webhook + validation handshake)
- `com.pfe.back.dto` — records w/ `jakarta.validation` (`DeployRequest`, `DashboardStats`, **`LoginRequest`, `RegisterRequest`, `AuthResponse`**).
- `com.pfe.back.config` — `SecurityConfig`, `DataSeeder`, **`WebSocketConfig`** (STOMP at `/ws`, broker `/topic/**`).
- **`com.pfe.back.azure`** — `AzureProperties` (`@ConfigurationProperties("app.azure")`), `AzureCredentialsProvider` (`ClientSecretCredential` + `AzureProfile`), `AzureResourceGraphClient` (Kusto sweeps with `@Retryable`). Soft-disables when `app.azure.*` is empty so the app still boots without Azure creds.
- **`com.pfe.back.infra`** — real-time Azure inventory engine:
  - `entity/` — `AzureResourceEntity` (idempotent on `azureId`, soft-deletes via `deletedAt`), `AzureResourceHistoryEntity` (`CREATE/UPDATE/DELETE/STATE_CHANGE/TAG_CHANGE/CONFIG_CHANGE` with JSON before/after), `VmStateEventEntity`, `SyncRunEntity`, `ProcessedEventEntity` (Event Grid dedupe key).
  - `repository/` — paginated search query on `AzureResourceRepository`.
  - `service/ResourceUpsertService` — **single transactional ingestion path** used by every source (Resource Graph poll, Event Grid, Terraform hook). Diffs JSON, appends history, broadcasts STOMP messages on `/topic/resources` and `/topic/vm-status`.
  - `service/ResourceSyncService` — `@Scheduled(fixedDelayString = "${app.infra.poll.rg-seconds:30}000")` Resource Graph sweep + public `runFullSync()` for the Terraform hook. Soft-deletes resources missing from the latest sweep.
  - `util/HmacVerifier` — constant-time SHA-256 HMAC for `/api/infra/sync/trigger`.

### Real-time Azure infra sync

Centralised inventory + change-stream for every Azure resource. Three ingestion paths feed one upsert pipeline:

1. **Periodic Resource Graph poll** (`@Scheduled`, every `app.infra.poll.rg-seconds`, default 30s) — full inventory sweep, source of truth for drift detection. Soft-deletes resources gone from Azure.
2. **Terraform GitHub Actions hook** — `.github/workflows/terraform.yml` posts an HMAC-signed JSON payload to `/api/infra/sync/trigger` after a successful `apply`/`destroy`. Backend immediately runs a full sync. Requires repo secrets `BACKEND_WEBHOOK_URL` + `INFRA_WEBHOOK_HMAC_SECRET` (which must match `APP_INFRA_WEBHOOK_SECRET`).
3. **Azure Event Grid webhook** at `/api/azure/events` — handles the EG validation handshake on first subscription, dedupes via `processed_event(event_id)`, then triggers a targeted sync.

Every change flows through `ResourceUpsertService.upsert(...)` which is the **only place** that writes to `azure_resource`, appends to `azure_resource_history`, and publishes to STOMP. New endpoints/sources should reuse it instead of writing entities directly.

`PfeBackApplication` is annotated `@EnableScheduling`, `@EnableAsync`, `@EnableRetry`. `SecurityConfig` permits `/api/azure/events`, `/api/infra/sync/trigger`, and `/ws/**` (HMAC + STOMP-level concerns guard them).

Required Maven deps (Boot 4 needs explicit version on `spring-retry`):

- `org.springframework.boot:spring-boot-starter-websocket`
- `com.azure:azure-identity` (1.13.3)
- `com.azure.resourcemanager:azure-resourcemanager` (2.43.0)
- `com.azure.resourcemanager:azure-resourcemanager-resourcegraph` (1.0.0 — newer versions aren't on Maven Central yet)
- `org.springframework.retry:spring-retry` **with explicit version `2.0.10`**
- `org.springframework:spring-aspects`

Application properties (all overridable via `APP_*` env vars):

```properties
app.azure.tenant-id=${APP_AZURE_TENANT_ID:}
app.azure.client-id=${APP_AZURE_CLIENT_ID:}
app.azure.client-secret=${APP_AZURE_CLIENT_SECRET:}
app.azure.subscription-id=${APP_AZURE_SUBSCRIPTION_ID:}
app.azure.resource-group=${APP_AZURE_RESOURCE_GROUP:}
app.infra.poll.enabled=${APP_INFRA_POLL_ENABLED:true}
app.infra.poll.rg-seconds=${APP_INFRA_POLL_RG_SECONDS:30}
app.infra.webhook-secret=${APP_INFRA_WEBHOOK_SECRET:change-me-in-prod}
```

Leaving `app.azure.*` empty puts the app in **DB-only mode** (Azure SDK clients log a warning and stay dormant) — useful for local dev without cloud creds. Full plan + testing checklist live in `docs/REALTIME_INFRA_PLAN.md`.

### Commands (from `pfe_back/`)

```powershell
.\mvnw.cmd spring-boot:run     # dev run, port 8080 (uses local MySQL on :3306)
.\mvnw.cmd clean package       # jar in target/
.\mvnw.cmd test                # run tests
.\mvnw.cmd test -Dtest=ClassName#method   # single test
```

## Frontend: `pfe_front`

- **Stack**: Angular **19**, **standalone components** (no `AppModule`), **Tailwind CSS v4** via `@tailwindcss/postcss` (configured in `.postcssrc.json`).
- **Bootstrap**: `src/main.ts` → `bootstrapApplication(AppComponent, appConfig)`.
- **Providers** in `src/app/app.config.ts`: `provideRouter(routes, withComponentInputBinding())`, `provideHttpClient(withFetch(), withInterceptors([authInterceptor]))`.
- **Routes** in `src/app/app.routes.ts`:
  - **Public**: `/login`, `/register` (guarded by `guestGuard` — redirects authenticated users to `/dashboard`).
  - **Protected** (under `authGuard`, rendered inside `ShellComponent` at `src/app/layout/`):
    - `/dashboard` — stats cards, SAFE/UNSAFE donut, severity bars, recent pipelines + alerts
    - `/images/safe` — table + deploy modal
    - `/images/unsafe` — read-only table with risk bars
    - `/pipelines` — recent CI/CD runs with status badges
    - `/alerts` — open/all toggle, acknowledge action
    - `/infrastructure` — node cards w/ CPU/mem/disk bars
    - `/infra-live` — **real-time Azure inventory** (live STOMP feed; rows flash on insert/update/delete; sync-run history)
    - `/infra` — Terraform IaC summary (read from `iac/resources.json`)
    - `/admin` — trigger SAFE deploy + audit log + recent deployments
  - All page components are lazy-loaded standalone components.
- **Auth**:
  - `src/app/core/auth.service.ts` — signal-based (`token`, `user`, `isAuthenticated`); persists to `localStorage('pfe-token', 'pfe-user')`. Methods: `login`, `register`, `me`, `logout`.
  - `src/app/core/auth.interceptor.ts` — adds `Authorization: Bearer <token>` to every request; on **401 OR 403** (not from `/auth/`), calls `logout()` and redirects to `/login`. (Spring Security returns 403 when the token is missing entirely, 401 when it's invalid — both mean "session is dead".)
  - `src/app/core/auth.guard.ts` — `authGuard` and `guestGuard`.
- **API client**: `src/app/core/api.service.ts` (single injectable using `inject(HttpClient)`); models in `src/app/core/models.ts`. Domain-specific services live alongside it: **`infra.service.ts`** (`/api/infra/resources*`, `/sync/runs`) and **`realtime.service.ts`** (single `@stomp/rx-stomp` connection over SockJS to `${environment.wsUrl}`, exposes `resourceChanges$()` and `vmStatus$()` typed Observables; JWT goes into the STOMP `CONNECT` headers).
- **API base URL + WS URL**: two environments — `environment.ts` (`apiBaseUrl: 'http://localhost:8080/api'`, `wsUrl: 'http://localhost:8080/ws'`, used by `ng serve`) and `environment.production.ts` (`apiBaseUrl: '/api'`, `wsUrl: '/ws'`, used by `ng build --configuration production` so the SPA goes through nginx). The swap is wired via `fileReplacements` in `angular.json`. `nginx.conf` proxies both `/api/` and `/ws` (with `Upgrade`/`Connection: upgrade` headers) to `backend:8080`.
- **Realtime deps** (in `package.json`): `@stomp/rx-stomp`, `sockjs-client`, `@types/sockjs-client`. Both are CommonJS; `angular.json` whitelists them via `allowedCommonJsDependencies` to silence the optimization-bailout warning.
- **Theming**:
  - `src/styles.css` imports Tailwind (`@import "tailwindcss";`) and defines a custom dark variant (`@custom-variant dark (&:where(.dark, .dark *))`).
  - Theme tokens are CSS variables under `:root` (light) and `.dark` (dark), exposed to Tailwind via `@theme inline` so utilities like `bg-surface`, `text-text-dim`, `border-border` resolve to the active theme.
  - **Dark/light toggle**: `src/app/core/theme.service.ts` — signal-based, persists to `localStorage('pfe-theme')`, falls back to `prefers-color-scheme`. Toggles the `dark` class on `<html>`. `src/index.html` has an inline boot script to set the class before first paint (no FOUC).
  - **Reusable component classes** (in `@layer components` of `styles.css`): `.card`, `.card-title`, `.btn`, `.btn-primary`, `.btn-danger`, `.btn-icon`, `.input`, `.select`, `.textarea`, `.field-label`, `.badge` + `.badge-{safe|unsafe|success|failed|running|info|warn|degraded|cancelled|critical|high|medium|low|up|down}`, `.bar` / `.bar-warn` / `.bar-danger`, `table.table` (with `.table-wrap` for horizontal scroll), `.mono`, `.muted`.
- **Shell**: `src/app/layout/shell.component.{ts,html}` renders sidebar + topbar (theme toggle, user info, logout button) and contains the protected `<router-outlet />`. `AppComponent` is now a thin `<router-outlet />` host so login/register render outside the shell.
- **Fonts**: Inter + JetBrains Mono loaded from Google Fonts in `index.html`.
- **Dockerfile** (`pfe_front/Dockerfile`): multi-stage — `node:20-alpine` runs `ng build --configuration production`, `nginx:1.27-alpine` serves `dist/pfe_front/browser`. `nginx.conf` does SPA history fallback + `/api/` reverse proxy to `http://backend:8080` + gzip + long-term cache for fingerprinted assets.

### Commands (from `pfe_front/`)

```powershell
npm start                # ng serve on http://localhost:4200 (dev API URL)
npm run build            # production build to dist/ (uses /api relative URL)
npm run watch            # dev build, rebuild on change
npm test                 # Karma + Jasmine
```

## Docker (full stack)

`docker-compose.yml` at repo root wires three services on the `pfe` network:

- `pfe-mysql` — MySQL 8.4 with a named `mysql_data` volume. Mapped to host **3307:3306** (host 3306 left free for a local MySQL). Healthcheck via `mysqladmin ping`.
- `pfe-backend` — built from `pfe_back/Dockerfile`. Waits for MySQL healthcheck. Env vars provide datasource + JWT config. Mapped to host **8080:8080**.
- `pfe-frontend` — built from `pfe_front/Dockerfile`. nginx on port 80, mapped to host **4200:80**. Proxies `/api/*` to `backend:8080`.

```powershell
docker compose up -d                # start everything
docker compose ps                   # status
docker compose logs -f backend      # tail backend logs
docker compose up -d --build        # rebuild images after code changes
docker compose stop                 # stop containers (keep them + DB volume)
docker compose down                 # remove containers + network (DB volume kept)
docker compose down -v              # also wipe DB volume
```

> **Port collision warning**: 4200 and 8080 are shared between Docker and local dev. Don't run `ng serve` / `mvnw spring-boot:run` while the Docker stack is up — one of them will fail to bind. Use Docker for "just run it" and the local commands when actively editing one side.

## Conventions

- **Adding a new feature**: entity → repository → service (use `AuditLogService.log(...)` for any state-changing action) → controller under `/api/...` → API method in `api.service.ts` → page component under `src/app/pages/<feature>/` → lazy route in `app.routes.ts` → sidebar link in `shell.component.html`.
- **State-changing endpoints must call `AuditLogService.log(action, actor, target, details, result)`** — both on success and on denial.
- **Only SAFE images deploy**: enforced in `DeploymentService`, not just the controller. UI hides the deploy action on UNSAFE images but the backend is the source of truth.
- **Naming**: folders/artifacts use snake_case (`pfe_back`, `pfe_front`); Java packages use `com.pfe.back.*`; Angular selectors `app-*`.
- **Shell**: Windows PowerShell — chain commands with `;` (not `&&`). Use `.\mvnw.cmd` for Maven.
- **Secrets**: never literal-commit DB passwords or JWT secrets. `application.properties` uses `${ENV_VAR:default}` placeholders; the only committed default is a dev-only JWT secret for local `mvnw spring-boot:run`. In Docker / prod, override with real values via `docker-compose.yml` env vars or your secret manager.
