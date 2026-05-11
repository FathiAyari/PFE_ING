package com.pfe.back.dto;

import jakarta.validation.constraints.NotBlank;

public record DeployRequest(
        @NotBlank String environment,
        @NotBlank String triggeredBy,
        String notes
) {}
