package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "security_alerts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    private String source;        // trivy, snyk, falco, ...
    private String cveId;
    private String affectedImage; // "nginx:1.21" etc.

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(nullable = false)
    private Instant createdAt;
}
