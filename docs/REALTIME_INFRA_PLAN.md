# Real-time Azure Infrastructure Sync — Plan

End-to-end plan to make the Spring Boot backend the source of truth for
Azure resources, fed by Event Grid + Resource Graph polling + Terraform
post-apply webhook, and pushed live to the Angular UI over STOMP/WebSocket.

> Implemented incrementally in 8 milestones. Milestones 1–6 are wired in
> this commit; M7 (Activity Log poller) and full topology graph are
> stubbed for follow-up work.

## Architecture

```
Azure Portal / CLI / Terraform / Auto-scaler
        │  (mutates resource)
        ▼
Azure Resource Manager
        │
        ├──▶ Event Grid System Topic ──▶ POST /api/azure/events ──┐
        │                                                          │
        ├──▶ Activity Log  ◀── ActivityLogPoller (60s, future) ────┤
        │                                                          │
        └──▶ Resource Graph ◀── ResourceSyncService (30s) ─────────┤
                                                                   ▼
GitHub Actions Terraform apply ──▶ POST /api/infra/sync/trigger ──▶ ResourceUpsertService
                                                                   │  (diff + persist + history)
                                                                   ▼
                                                  ApplicationEventPublisher
                                                                   │
                                                                   ▼
                                                ResourceChangeBroadcaster (STOMP)
                                                                   │
                                          ┌────────────────────────┼─────────────────────────┐
                                          ▼                        ▼                         ▼
                                  /topic/resources         /topic/vm-status          /topic/sync-runs
```

## Milestones

### M1 — DB schema (JPA `ddl-auto=update`)

Tables (entities under `com.pfe.back.infra.entity`):

- **`azure_resource`** — current observed state, idempotent upsert key = `azure_id`. Soft-deletes via `deleted_at`.
- **`azure_resource_history`** — append-only audit (CREATE/UPDATE/DELETE/STATE_CHANGE/TAG_CHANGE/CONFIG_CHANGE) with JSON before/after/diff.
- **`vm_state_event`** — focused VM lifecycle stream (`power_state`, `provisioning_state`).
- **`sync_run`** — one row per poll/webhook execution, for ops visibility.
- **`processed_event`** — Event Grid `event_id` dedupe.

### M2 — Azure SDK layer (`com.pfe.back.azure`)

- `AzureProperties` — `app.azure.*` config bound from env.
- `AzureCredentialsProvider` — `ClientSecretCredential` + `AzureProfile`.
- `AzureResourceGraphClient` — wraps `ResourceGraphManager`, runs Kusto.
- `AzureComputeClient` — `getInstanceView(vmId)` for accurate VM power state.

### M3 — Sync engine (`com.pfe.back.infra.service`)

- `ResourceUpsertService.upsert(...)` — single transactional method used by every ingestion path. Diffs JSON, writes history, publishes `ResourceChangedEvent`.
- `ResourceSyncService` — `@Scheduled(fixedDelay=30s)` Resource Graph sweep, also `runFullSync()` for the Terraform hook.
- `ResourceChangeBroadcaster` — STOMP publisher on `/topic/resources` and `/topic/vm-status`.

### M4 — Webhook & REST controllers

- `InfraSyncController` — `POST /api/infra/sync/trigger` (HMAC-protected).
- `EventGridWebhookController` — `POST /api/azure/events` (handles EG validation handshake + dedupes).
- `InfraResourceController` — list/detail/history/topology REST.

### M5 — Terraform workflow integration

`.github/workflows/terraform.yml` post-apply step posts an HMAC-signed payload to `${{ secrets.BACKEND_WEBHOOK_URL }}`.

### M6 — Angular live UI

- `realtime.service.ts` — `@stomp/rx-stomp` over `${environment.wsUrl}` with JWT in CONNECT headers.
- `infra.service.ts` — REST.
- `pages/infra-live/` — live inventory table that merges STOMP messages by `azureId`; row flashes on insert/update/delete.

### M7 — Hardening (follow-up)

- Activity Log poller (60s).
- Cytoscape topology graph.
- Toast notifications via `notifications.service.ts`.
- Spring Retry on Azure SDK calls.

## GitHub repository secrets needed

| Secret | Purpose |
|---|---|
| `AZURE_TENANT_ID` / `AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET` / `AZURE_SUBSCRIPTION_ID` | Already used by Terraform; reused by backend |
| `INFRA_WEBHOOK_HMAC_SECRET` | Shared secret between workflow and backend |
| `BACKEND_WEBHOOK_URL` | Public URL of the backend, e.g. `https://api.example.com/api/infra/sync/trigger` |

## Backend env vars (docker-compose.yml `backend.environment`)

```
APP_AZURE_TENANT_ID
APP_AZURE_CLIENT_ID
APP_AZURE_CLIENT_SECRET
APP_AZURE_SUBSCRIPTION_ID
APP_AZURE_RESOURCE_GROUP        (optional filter)
APP_INFRA_WEBHOOK_SECRET
APP_INFRA_POLL_RG_SECONDS=30
APP_INFRA_POLL_ENABLED=true
```

## Testing checklist

| # | Action | Expected | Latency |
|---|--------|----------|---------|
| 1 | Run Terraform apply via GH Actions | Resources appear in `/api/infra/resources` and live UI | ≤ 10s after workflow finish |
| 2 | `az vm stop` in CLI | VM badge → Stopped, `vm_state_event` row, history entry | ≤ 30s (poll fallback) / ≤ 5s (Event Grid) |
| 3 | Add tag via Portal | History `TAG_CHANGE` with diff | ≤ 30s |
| 4 | Delete NSG in Portal | Row marked deleted, red flash | ≤ 30s |
| 5 | Backend restart mid-stream | Resumes from cursor | n/a |
| 6 | Invalid HMAC on `/api/infra/sync/trigger` | 401 | n/a |

## Rollout order

1. Add Azure SDK deps + entities + Repos → `mvn compile` passes.
2. Properties + credentials provider + Resource Graph client → smoke-test `@PostConstruct` log.
3. Enable scheduler — DB rows populate.
4. REST endpoints — fetch via Postman.
5. WebSocket + broadcaster — verify with browser console.
6. Terraform hook — test with manual `gh workflow run`.
7. Event Grid subscription (manual one-time setup in Azure).
8. Angular page → routes → nav → ship.
