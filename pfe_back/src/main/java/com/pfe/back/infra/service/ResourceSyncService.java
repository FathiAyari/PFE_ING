package com.pfe.back.infra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.back.azure.AzureResourceGraphClient;
import com.pfe.back.infra.entity.SyncRunEntity;
import com.pfe.back.infra.entity.SyncRunEntity.Kind;
import com.pfe.back.infra.entity.SyncRunEntity.Status;
import com.pfe.back.infra.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Continuous reconciler. Pulls the full inventory from Azure Resource Graph
 * every {@code app.infra.poll.rg-seconds} seconds and feeds it through
 * {@link ResourceUpsertService}. Resources missing from the sweep are
 * soft-deleted. Also exposes {@link #runFullSync(String, Kind)} for the
 * Terraform webhook.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceSyncService {

    private final AzureResourceGraphClient resourceGraph;
    private final ResourceUpsertService upsertService;
    private final SyncRunRepository syncRunRepo;

    @Value("${app.infra.poll.enabled:true}")
    private boolean pollEnabled;

    @Scheduled(fixedDelayString = "${app.infra.poll.rg-seconds:30}000",
            initialDelayString = "10000")
    public void scheduledSweep() {
        if (!pollEnabled) return;
        if (!resourceGraph.isReady()) return;
        runFullSync(null, Kind.RG_DELTA);
    }

    public SyncRunEntity runFullSync(String correlationId, Kind kind) {
        SyncRunEntity run = syncRunRepo.save(SyncRunEntity.builder()
                .kind(kind)
                .status(Status.RUNNING)
                .correlationId(correlationId)
                .startedAt(Instant.now())
                .build());
        int seen = 0, changed = 0;
        try {
            List<JsonNode> resources = resourceGraph.listAllResources();
            Set<String> ids = new HashSet<>();
            for (JsonNode node : resources) {
                JsonNode idN = node.get("id");
                if (idN == null) continue;
                ids.add(idN.asText());
                var saved = upsertService.upsert(node, "RESOURCE_GRAPH", correlationId);
                if (saved != null) seen++;
            }
            int removed = upsertService.softDeleteMissing(ids, "RESOURCE_GRAPH");
            changed = removed;
            run.setStatus(Status.OK);
            run.setResourcesSeen(seen);
            run.setResourcesChanged(changed);
            log.info("✅ azure_resource table filled: {} resources upserted, {} soft-deleted (kind={}, runId={})",
                    seen, removed, kind, run.getId());
        } catch (Exception e) {
            log.error("Sync run failed: {}", e.getMessage(), e);
            run.setStatus(Status.ERROR);
            run.setError(e.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            syncRunRepo.save(run);
        }
        return run;
    }
}
