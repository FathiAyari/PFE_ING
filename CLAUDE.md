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
- `com.pfe.back.dto` — records w/ `jakarta.validation` (`DeployRequest`, `DashboardStats`, **`LoginRequest`, `RegisterRequest`, `AuthResponse`**).
- `com.pfe.back.config` — `SecurityConfig`, `DataSeeder`.

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
    - `/admin` — trigger SAFE deploy + audit log + recent deployments
  - All page components are lazy-loaded standalone components.
- **Auth**:
  - `src/app/core/auth.service.ts` — signal-based (`token`, `user`, `isAuthenticated`); persists to `localStorage('pfe-token', 'pfe-user')`. Methods: `login`, `register`, `me`, `logout`.
  - `src/app/core/auth.interceptor.ts` — adds `Authorization: Bearer <token>` to every request; on **401 OR 403** (not from `/auth/`), calls `logout()` and redirects to `/login`. (Spring Security returns 403 when the token is missing entirely, 401 when it's invalid — both mean "session is dead".)
  - `src/app/core/auth.guard.ts` — `authGuard` and `guestGuard`.
- **API client**: `src/app/core/api.service.ts` (single injectable using `inject(HttpClient)`); models in `src/app/core/models.ts`.
- **API base URL**: two environments — `environment.ts` (`http://localhost:8080/api`, used by `ng serve`) and `environment.production.ts` (`/api`, used by `ng build --configuration production` so the SPA goes through nginx). The swap is wired via `fileReplacements` in `angular.json`.
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
