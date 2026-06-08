package com.pfe.back.infra.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_run", indexes = {
        @Index(name = "idx_sync_run_kind", columnList = "kind,startedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncRunEntity {

    public enum Kind { FULL, RG_DELTA, ACTIVITY_LOG, EVENT_GRID, TERRAFORM_HOOK }
    public enum Status { RUNNING, OK, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private Instant startedAt;
    private Instant finishedAt;

    private Integer resourcesSeen;
    private Integer resourcesChanged;

    @Column(length = 128)
    private String correlationId;

    @Column(length = 1024)
    private String error;
}
