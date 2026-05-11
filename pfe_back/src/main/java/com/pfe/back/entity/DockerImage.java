package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "docker_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DockerImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private String registry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    /** Reason it is UNSAFE (e.g. "CVE-2024-1234 - critical"). Null for SAFE. */
    @Column(length = 1000)
    private String reason;

    /** Number of vulnerabilities found by the scanner. */
    private int vulnerabilityCount;

    /** Trivy / scanner score, 0..10. */
    private double riskScore;

    @Column(nullable = false)
    private Instant scannedAt;

    private String digest;
    private Long sizeBytes;
}
