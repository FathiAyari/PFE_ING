package com.pfe.back.infra.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Append-only audit trail for every change ingested from Azure. One row
 * per change; never updated or deleted.
 */
@Entity
@Table(name = "azure_resource_history", indexes = {
        @Index(name = "idx_arh_azure_id", columnList = "azureId,occurredAt"),
        @Index(name = "idx_arh_change_type", columnList = "changeType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AzureResourceHistoryEntity {

    public enum ChangeType {
        CREATE, UPDATE, DELETE, STATE_CHANGE, TAG_CHANGE, CONFIG_CHANGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String azureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChangeType changeType;

    @Lob @Column(columnDefinition = "JSON")
    private String beforeJson;

    @Lob @Column(columnDefinition = "JSON")
    private String afterJson;

    @Lob @Column(columnDefinition = "JSON")
    private String diffJson;

    @Column(length = 128)
    private String correlationId;

    @Column(length = 32)
    private String source;

    private Instant occurredAt;
    private Instant recordedAt;
}
