package com.pfe.back.infra.dto;

import com.pfe.back.infra.entity.AzureResourceEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** Lightweight projection sent to REST + STOMP clients. */
@Data
@Builder
public class AzureResourceDto {
    private Long id;
    private String azureId;
    private String name;
    private String type;
    private String kind;
    private String resourceGroup;
    private String subscriptionId;
    private String location;
    private String provisioningState;
    private String powerState;
    private String tags;
    private String sku;
    private String source;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant lastChangedAt;
    private Instant deletedAt;

    public static AzureResourceDto from(AzureResourceEntity e) {
        return AzureResourceDto.builder()
                .id(e.getId())
                .azureId(e.getAzureId())
                .name(e.getName())
                .type(e.getType())
                .kind(e.getKind())
                .resourceGroup(e.getResourceGroup())
                .subscriptionId(e.getSubscriptionId())
                .location(e.getLocation())
                .provisioningState(e.getProvisioningState())
                .powerState(e.getPowerState())
                .tags(e.getTags())
                .sku(e.getSku())
                .source(e.getSource())
                .firstSeenAt(e.getFirstSeenAt())
                .lastSeenAt(e.getLastSeenAt())
                .lastChangedAt(e.getLastChangedAt())
                .deletedAt(e.getDeletedAt())
                .build();
    }
}
