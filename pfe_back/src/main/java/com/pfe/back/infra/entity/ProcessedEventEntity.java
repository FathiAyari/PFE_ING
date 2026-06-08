package com.pfe.back.infra.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Idempotency table for Event Grid deliveries. */
@Entity
@Table(name = "processed_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEventEntity {

    @Id
    @Column(length = 128)
    private String eventId;

    private Instant receivedAt;
}
