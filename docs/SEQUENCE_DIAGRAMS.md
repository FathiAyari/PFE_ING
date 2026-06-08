# Sequence Diagrams

Core sequence diagrams for authentication, workflow webhook sync, and live page refresh.

## 1) Terraform Workflow -> Backend Sync

```mermaid
sequenceDiagram
  autonumber
  participant GH as GitHub Actions
  participant TF as Terraform
  participant BK as Backend
  participant ARG as Azure Resource Graph
  participant DB as MySQL
  participant WS as WebSocket Topic

  GH->>TF: terraform apply
  TF-->>GH: outputs (vm_name, vm_public_ip, acr_name...)
  GH->>GH: Build BODY + HMAC signature
  GH->>BK: POST /api/infra/sync/trigger\nX-Hub-Signature-256
  BK->>BK: Verify HMAC
  alt Signature invalid
    BK-->>GH: 401 invalid signature
  else Signature valid
    BK->>DB: INSERT sync_run (RUNNING)
    BK->>ARG: listAllResources()
    ARG-->>BK: List<JsonNode> resources
    loop each resource
      BK->>DB: UPSERT azure_resource
      BK->>DB: INSERT azure_resource_history (if changed)
      BK->>WS: /topic/resources event
    end
    BK->>DB: soft-delete missing resources
    BK->>DB: UPDATE sync_run (OK/ERROR)
    BK-->>GH: 202 Accepted {runId, status,...}
  end
```

## 2) Frontend Live Page Data Path

```mermaid
sequenceDiagram
  autonumber
  participant UI as Angular /infra-live
  participant BK as Backend API
  participant WS as STOMP Broker

  UI->>BK: GET /api/infra/resources
  BK-->>UI: page {content,total,...}
  UI->>BK: GET /api/infra/sync/runs
  BK-->>UI: sync run list
  UI->>WS: SUBSCRIBE /topic/resources
  UI->>WS: SUBSCRIBE /topic/vm-status
  WS-->>UI: ResourceChangeMessage events
  UI->>UI: Merge rows + flash create/update/delete
```

## 3) Login Flow (JWT)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant UI as Angular Login
  participant BK as AuthController

  U->>UI: Enter username/password
  UI->>BK: POST /api/auth/login
  BK-->>UI: {token, user, expiresAt}
  UI->>UI: store token in localStorage
  UI->>BK: GET protected API with Bearer token
  BK-->>UI: protected data
```

