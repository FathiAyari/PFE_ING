# Use Case Diagram

This document captures the main platform actors and use cases.

## System Boundary

System: **PFE DevSecOps Platform**

- Angular frontend (`pfe_front`)
- Spring backend (`pfe_back`)
- Terraform infrastructure (`infra`)

## Actors

- **Cloud Admin**: logs in, monitors infra, views audit/security/pipeline pages.
- **GitHub Actions**: runs Terraform and triggers backend sync.
- **Azure Resource Graph**: provides cloud resource inventory.
- **Azure Event Grid**: optional event-driven updates.

## Use Cases (text diagram)

```text
Cloud Admin
  -> Login
  -> View dashboard pages
  -> View Live Infrastructure (/infra-live)
  -> Inspect sync runs and resource history

GitHub Actions
  -> Run terraform plan/apply/destroy
  -> Notify backend webhook (/api/infra/sync/trigger)

Backend
  -> Verify webhook HMAC
  -> Run full Azure sync
  -> Persist resources/history
  -> Broadcast live updates (STOMP)

Azure
  -> Return resources via Resource Graph query
  -> Emit infra events (Event Grid, optional)
```

## Mermaid Use Case (lightweight)

```mermaid
flowchart LR
  A[Cloud Admin] --> U1[Login]
  A --> U2[View Live Infrastructure]
  A --> U3[View Sync Runs]

  G[GitHub Actions] --> U4[Run Terraform Apply]
  G --> U5[POST /api/infra/sync/trigger]

  U5 --> B[Backend HMAC Verification]
  B --> U6[Run Full Sync]
  U6 --> R[Azure Resource Graph]
  U6 --> D[(MySQL Tables)]
  U6 --> W[/topic/resources]

  E[Azure Event Grid] --> U7[POST /api/azure/events]
  U7 --> U6
```

## Notes

- Primary operational path today is workflow-triggered full sync + periodic poll.
- Event Grid path is supported for near-real-time updates when configured.

