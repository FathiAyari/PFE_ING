package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An application onboarded into PFE (e.g. PeopleOps, RecruitPro). Holds the
 * onboarding request details, its lifecycle status, the Azure resources that
 * belong to it, and the integration credentials issued once READY.
 *
 * <p>Note: provisioning is <b>simulated</b> — approving a request registers the
 * derived Azure resource names in {@link ApplicationResource} rows instead of
 * running Terraform.
 */
@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- General information ----
    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 120)
    private String team;

    @Column(length = 300)
    private String repositoryUrl;

    @Column(length = 160)
    private String contactEmail;

    // ---- Infrastructure requirements ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentType deploymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 20)
    private DatabaseType database;

    @Column(nullable = false)
    private boolean needsContainerRegistry;

    @Column(nullable = false)
    private boolean needsKeyVault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppEnvironment environment;

    // ---- Lifecycle ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(length = 1000)
    private String rejectionReason;

    // ---- Integration credentials (issued on approval) ----
    /** Human-friendly code, e.g. APP-001. */
    @Column(length = 20)
    private String applicationCode;

    /** Bearer-style token the app's CI uses to talk to PFE. */
    @Column(length = 80)
    private String integrationToken;

    // ---- Audit timestamps ----
    @Column(nullable = false)
    private Instant createdAt;

    private Instant decidedAt;
    private String decidedBy;

    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApplicationResource> resources = new ArrayList<>();
}

