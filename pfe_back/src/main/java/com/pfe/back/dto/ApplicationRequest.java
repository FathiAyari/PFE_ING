package com.pfe.back.dto;

import com.pfe.back.entity.AppEnvironment;
import com.pfe.back.entity.DatabaseType;
import com.pfe.back.entity.DeploymentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Payload for a developer's "Request New Application" form. */
public record ApplicationRequest(
        @NotBlank String name,
        String description,
        String team,
        String repositoryUrl,
        @Email String contactEmail,
        @NotNull DeploymentType deploymentType,
        @NotNull DatabaseType database,
        boolean needsContainerRegistry,
        boolean needsKeyVault,
        @NotNull AppEnvironment environment
) {}

