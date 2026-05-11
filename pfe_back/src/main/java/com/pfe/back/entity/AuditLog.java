package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action;        // DEPLOY_IMAGE, ACK_ALERT, BLOCK_IMAGE, ...

    @Column(nullable = false)
    private String actor;         // user or system

    @Column(nullable = false)
    private String target;        // image:tag, alert#42, ...

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private String result;        // OK, DENIED, ERROR

    @Column(nullable = false)
    private Instant timestamp;
}
