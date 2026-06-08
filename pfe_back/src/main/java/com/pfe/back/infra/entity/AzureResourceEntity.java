package com.pfe.back.infra.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Current observed state of a single Azure resource. Idempotent upserts
 * are keyed on {@link #azureId} (the full ARM resource id).
 */
@Entity
@Table(name = "azure_resource", indexes = {
        @Index(name = "idx_azure_resource_type", columnList = "type"),
        @Index(name = "idx_azure_resource_rg", columnList = "resourceGroup"),
        @Index(name = "idx_azure_resource_state", columnList = "provisioningState"),
        @Index(name = "idx_azure_resource_deleted", columnList = "deletedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AzureResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String azureId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String type;          // e.g. Microsoft.Compute/virtualMachines

    @Column(length = 64)
    private String kind;

    @Column(length = 128)
    private String resourceGroup;

    @Column(length = 64)
    private String subscriptionId;

    @Column(length = 64)
    private String location;

    @Column(length = 64)
    private String provisioningState;

    /** VM-only, e.g. PowerState/running */
    @Column(length = 64)
    private String powerState;

    @Lob
    @Column(columnDefinition = "JSON")
    private String sku;

    @Lob
    @Column(columnDefinition = "JSON")
    private String tags;

    @Lob
    @Column(columnDefinition = "JSON")
    private String properties;

    @Column(length = 32)
    private String source;        // EVENT_GRID, RESOURCE_GRAPH, ACTIVITY_LOG, TERRAFORM_HOOK, MANUAL

    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant lastChangedAt;
    private Instant deletedAt;
}
