# PeopleOps — Context Prompt

Paste this into any AI tool (Claude Code, Cursor, ChatGPT, Copilot Chat) inside
an **empty directory**. It tells the AI what to build and, crucially, what the
existing DevSecOps platform ("PFE") expects from it.

---

## Context

You are scaffolding a new project called **PeopleOps** — a small HR management
web app. It is **not** a standalone product; it exists to be **monitored** by
an existing DevSecOps platform called **PFE**.

### What PFE is (the parent platform)

PFE is a fully-built DevSecOps control plane running at `http://localhost:4200`
(Angular 19) with a Spring Boot 4 backend at `http://localhost:8080/api`. It
already provides:

- **Docker image inventory** classified as `SAFE` or `UNSAFE`
  (UNSAFE = has CRITICAL CVEs and cannot be deployed).
- **Security alerts** feed (CVEs discovered in scanned images).
- **CI/CD pipeline history** (build status, duration, commit, branch).
- **Deployment gate**: `POST /api/deployments/images/{id}` — the backend
  **rejects** UNSAFE images and records the denial in an audit log.
- **Real-time Azure infrastructure inventory** (Terraform-managed resources
  visible in a live UI with WebSocket updates).
- **Immutable audit log** of every state-changing action.
- **JWT auth** with a single `CLOUD_ADMIN` role (`admin / admin123` seeded).

PFE is generic on purpose — any application can adopt it by hitting a few REST
endpoints from its CI pipeline. **PeopleOps is the first such adopter.**

### The four PFE endpoints PeopleOps will call from CI

All require `Authorization: Bearer <PFE_ADMIN_TOKEN>`.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/images` | Register a scanned Docker image (`{name, tag, status, criticalCount, digest}`). Returns `{id}`. |
| `POST` | `/api/alerts` | Publish a CVE finding (`{severity, cve, title, description, image}`). |
| `POST` | `/api/pipelines` | Report a CI run (`{name, branch, commit, status, durationMs}`). |
| `POST` | `/api/deployments/images/{id}` | Request deployment. **Returns 4xx if the image is UNSAFE** — this is the gate. |

### Why PeopleOps is a good target for a DevSecOps demo

PeopleOps stores **PII** (employee salaries + national IDs). Any critical CVE
in its images is high-stakes. That makes the "deploy blocked because UNSAFE"
demo path meaningful, not artificial.

### PeopleOps itself (what you're actually building)

A minimal HR app with three services, one shared Postgres DB, one admin role:

- Employees: CRUD.
- Leave requests: submit → approve/reject → balance updates.
- Payroll: monthly worker generates snapshots.

Small, clean, PII-heavy. No swagger, no metrics stack, no multi-tenant.

### Success criteria for the demo

1. `docker compose up -d` launches PeopleOps (api + web + worker + db).
2. `git push` on `main` triggers a GitHub Actions workflow that:
   - Builds all 3 Docker images.
   - Scans each with Trivy.
   - Posts image + findings + pipeline run to the PFE backend.
   - Attempts a deployment — PFE gates it based on CVE count.
3. Within seconds, the PFE dashboard at `http://localhost:4200` shows:
   - Updated SAFE/UNSAFE donut.
   - New CVE alerts.
   - New pipeline row.
   - Successful deploys (or DENIED audit rows).

That's the story. Build PeopleOps so this story works end-to-end.
