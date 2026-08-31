package com.pfe.back.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * An Azure resource that belongs to an {@link Application}. Registered when the
 * onboarding request is approved (simulated Terraform output), not provisioned
 * for real.
 */
@Entity
@Table(name = "application_resources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    /** RESOURCE_GROUP, CONTAINER_REGISTRY, CONTAINER_APP, VM, AKS, POSTGRESQL, KEY_VAULT. */
    @Column(nullable = false, length = 40)
    private String resourceType;

    /** The resource name / identifier, e.g. peopleops-rg, peopleopsacr.azurecr.io. */
    @Column(nullable = false, length = 200)
    private String identifier;
}

