package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pipeline_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;          // e.g. "build-backend"

    @Column(nullable = false)
    private String branch;        // e.g. "main"

    private String commitSha;
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;
    private Long durationSeconds;

    /** Stage that produced an UNSAFE verdict, if any. */
    private String failureStage;
}
