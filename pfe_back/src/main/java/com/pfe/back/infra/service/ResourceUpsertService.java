package com.pfe.back.infra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.back.infra.dto.AzureResourceDto;
import com.pfe.back.infra.dto.ResourceChangeMessage;
import com.pfe.back.infra.entity.AzureResourceEntity;
import com.pfe.back.infra.entity.AzureResourceHistoryEntity;
import com.pfe.back.infra.entity.AzureResourceHistoryEntity.ChangeType;
import com.pfe.back.infra.entity.VmStateEventEntity;
import com.pfe.back.infra.repository.AzureResourceHistoryRepository;
import com.pfe.back.infra.repository.AzureResourceRepository;
import com.pfe.back.infra.repository.VmStateEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Single transactional entry point for ingesting Azure resource state from
 * any source (Resource Graph, Event Grid, Activity Log, Terraform hook).
 * Handles diffing, history append, soft-delete, and the live STOMP broadcast.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceUpsertService {

    private final AzureResourceRepository resourceRepo;
    private final AzureResourceHistoryRepository historyRepo;
    private final VmStateEventRepository vmStateRepo;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;

    @Transactional
    public AzureResourceEntity upsert(JsonNode azureJson, String source, String correlationId) {
        String azureId = text(azureJson, "id");
        if (azureId == null || azureId.isBlank()) return null;

        Optional<AzureResourceEntity> existingOpt = resourceRepo.findByAzureId(azureId);
        Instant now = Instant.now();

        AzureResourceEntity before = existingOpt.map(this::clone).orElse(null);
        AzureResourceEntity entity = existingOpt.orElseGet(() -> AzureResourceEntity.builder()
                .azureId(azureId)
                .firstSeenAt(now)
                .build());

        // Apply incoming fields
        entity.setName(text(azureJson, "name"));
        entity.setType(text(azureJson, "type"));
        entity.setKind(text(azureJson, "kind"));
        entity.setResourceGroup(text(azureJson, "resourceGroup"));
        entity.setSubscriptionId(text(azureJson, "subscriptionId"));
        entity.setLocation(text(azureJson, "location"));
        entity.setTags(jsonField(azureJson, "tags"));
        entity.setSku(jsonField(azureJson, "sku"));
        entity.setProperties(jsonField(azureJson, "properties"));

        JsonNode props = azureJson.get("properties");
        if (props != null) {
            entity.setProvisioningState(text(props, "provisioningState"));
        }
        // VM power state — Resource Graph projects it under properties.extended.instanceView.powerState.code
        // or via a separate query; accept "powerState" top-level too.
        String powerState = text(azureJson, "powerState");
        if (powerState == null && props != null) {
            JsonNode iv = props.path("extended").path("instanceView").path("powerState").path("code");
            if (iv.isTextual()) powerState = iv.asText();
        }
        String prevPowerState = entity.getPowerState();
        entity.setPowerState(powerState);

        entity.setSource(source);
        entity.setLastSeenAt(now);
        entity.setDeletedAt(null);

        // Detect change
        ChangeType changeType = before == null ? ChangeType.CREATE : detectChange(before, entity);
        if (changeType != null) {
            entity.setLastChangedAt(now);
        }

        AzureResourceEntity saved = resourceRepo.save(entity);

        if (changeType != null) {
            writeHistory(saved, before, changeType, source, correlationId, now);
            broadcast(changeType, saved);
            if ("Microsoft.Compute/virtualMachines".equalsIgnoreCase(saved.getType())
                    && !Objects.equals(prevPowerState, saved.getPowerState())) {
                writeVmEvent(saved, prevPowerState, source, now);
            }
        }
        return saved;
    }

    @Transactional
    public void markDeleted(String azureId, String source, String correlationId) {
        Optional<AzureResourceEntity> opt = resourceRepo.findByAzureId(azureId);
        if (opt.isEmpty()) return;
        AzureResourceEntity entity = opt.get();
        if (entity.getDeletedAt() != null) return;
        AzureResourceEntity before = clone(entity);
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setLastChangedAt(now);
        entity.setSource(source);
        AzureResourceEntity saved = resourceRepo.save(entity);
        writeHistory(saved, before, ChangeType.DELETE, source, correlationId, now);
        broadcast(ChangeType.DELETE, saved);
    }

    /** Soft-delete every resource not seen in the latest sweep. */
    @Transactional
    public int softDeleteMissing(java.util.Set<String> seenAzureIds, String source) {
        int n = 0;
        for (AzureResourceEntity alive : resourceRepo.findAllByDeletedAtIsNull()) {
            if (!seenAzureIds.contains(alive.getAzureId())) {
                markDeleted(alive.getAzureId(), source, null);
                n++;
            }
        }
        return n;
    }

    private ChangeType detectChange(AzureResourceEntity before, AzureResourceEntity after) {
        if (!Objects.equals(before.getProvisioningState(), after.getProvisioningState())
                || !Objects.equals(before.getPowerState(), after.getPowerState())) {
            return ChangeType.STATE_CHANGE;
        }
        if (!Objects.equals(before.getTags(), after.getTags())) {
            return ChangeType.TAG_CHANGE;
        }
        if (!Objects.equals(before.getProperties(), after.getProperties())
                || !Objects.equals(before.getSku(), after.getSku())) {
            return ChangeType.CONFIG_CHANGE;
        }
        return null;
    }

    private void writeHistory(AzureResourceEntity after, AzureResourceEntity before,
                              ChangeType type, String source, String correlationId, Instant when) {
        try {
            historyRepo.save(AzureResourceHistoryEntity.builder()
                    .azureId(after.getAzureId())
                    .changeType(type)
                    .beforeJson(before == null ? null : objectMapper.writeValueAsString(AzureResourceDto.from(before)))
                    .afterJson(objectMapper.writeValueAsString(AzureResourceDto.from(after)))
                    .source(source)
                    .correlationId(correlationId)
                    .occurredAt(when)
                    .recordedAt(when)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write history for {}: {}", after.getAzureId(), e.getMessage());
        }
    }

    private void writeVmEvent(AzureResourceEntity vm, String previous, String source, Instant when) {
        vmStateRepo.save(VmStateEventEntity.builder()
                .azureId(vm.getAzureId())
                .previousPowerState(previous)
                .powerState(vm.getPowerState())
                .provisioningState(vm.getProvisioningState())
                .source(source)
                .occurredAt(when)
                .build());
        messaging.convertAndSend("/topic/vm-status", AzureResourceDto.from(vm));
    }

    private void broadcast(ChangeType change, AzureResourceEntity entity) {
        try {
            messaging.convertAndSend("/topic/resources",
                    new ResourceChangeMessage(change, AzureResourceDto.from(entity)));
        } catch (Exception e) {
            log.debug("Broadcast skipped: {}", e.getMessage());
        }
    }

    private AzureResourceEntity clone(AzureResourceEntity src) {
        return AzureResourceEntity.builder()
                .id(src.getId()).azureId(src.getAzureId()).name(src.getName())
                .type(src.getType()).kind(src.getKind())
                .resourceGroup(src.getResourceGroup()).subscriptionId(src.getSubscriptionId())
                .location(src.getLocation())
                .provisioningState(src.getProvisioningState()).powerState(src.getPowerState())
                .sku(src.getSku()).tags(src.getTags()).properties(src.getProperties())
                .source(src.getSource())
                .firstSeenAt(src.getFirstSeenAt()).lastSeenAt(src.getLastSeenAt())
                .lastChangedAt(src.getLastChangedAt()).deletedAt(src.getDeletedAt())
                .build();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String jsonField(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return null;
        }
    }
}
