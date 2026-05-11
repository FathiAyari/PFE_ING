package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "deployments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_id")
    private DockerImage image;

    @Column(nullable = false)
    private String environment;   // dev, staging, prod

    @Column(nullable = false)
    private String status;        // SUCCESS, FAILED, IN_PROGRESS

    private String triggeredBy;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private Instant deployedAt;
}
