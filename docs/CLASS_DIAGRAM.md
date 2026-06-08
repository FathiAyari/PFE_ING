# Class Diagram

This document highlights the core backend classes and their relationships for live infrastructure sync.

## Scope

Focus: webhook -> sync service -> upsert -> persistence -> broadcast.

## Mermaid Class Diagram

```mermaid
classDiagram
  class InfraSyncController {
    +trigger(signature, rawBody) ResponseEntity
    -secret : String
  }

  class ResourceSyncService {
    +scheduledSweep()
    +runFullSync(correlationId, kind) SyncRunEntity
  }

  class ResourceUpsertService {
    +upsert(azureJson, source, correlationId) AzureResourceEntity
    +markDeleted(azureId, source, correlationId)
    +softDeleteMissing(ids, source) int
  }

  class AzureResourceGraphClient {
    +isReady() boolean
    +listAllResources() List~JsonNode~
  }

  class AzureResourceEntity {
    +id : Long
    +azureId : String
    +name : String
    +type : String
    +resourceGroup : String
    +subscriptionId : String
    +location : String
    +provisioningState : String
    +powerState : String
    +deletedAt : Instant
  }

  class AzureResourceHistoryEntity
  class VmStateEventEntity
  class SyncRunEntity

  class AzureResourceRepository
  class AzureResourceHistoryRepository
  class VmStateEventRepository
  class SyncRunRepository

  class SimpMessagingTemplate

  InfraSyncController --> ResourceSyncService
  ResourceSyncService --> AzureResourceGraphClient
  ResourceSyncService --> ResourceUpsertService
  ResourceSyncService --> SyncRunRepository

  ResourceUpsertService --> AzureResourceRepository
  ResourceUpsertService --> AzureResourceHistoryRepository
  ResourceUpsertService --> VmStateEventRepository
  ResourceUpsertService --> SimpMessagingTemplate

  AzureResourceRepository --> AzureResourceEntity
  AzureResourceHistoryRepository --> AzureResourceHistoryEntity
  VmStateEventRepository --> VmStateEventEntity
  SyncRunRepository --> SyncRunEntity
```

## Notes

- `ResourceUpsertService` is the single write path for infra resources.
- `azure_resource` is keyed by `azureId` (full ARM id).
- Live UI updates are emitted via STOMP topics `/topic/resources` and `/topic/vm-status`.

