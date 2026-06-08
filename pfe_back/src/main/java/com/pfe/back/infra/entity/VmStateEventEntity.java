package com.pfe.back.infra.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "vm_state_event", indexes = {
        @Index(name = "idx_vmse_vm", columnList = "azureId,occurredAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VmStateEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String azureId;

    @Column(length = 64)
    private String previousPowerState;

    @Column(length = 64)
    private String powerState;

    @Column(length = 64)
    private String provisioningState;

    @Column(length = 32)
    private String source;

    private Instant occurredAt;
}
