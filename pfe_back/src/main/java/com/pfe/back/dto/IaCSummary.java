package com.pfe.back.dto;

import java.util.List;

/** Summary returned by `GET /api/iac/resources`. */
public record IaCSummary(
        String project,
        String provider,
        String location,
        boolean stateApplied,   // true if infra/terraform.tfstate exists and has resources
        int totalResources,
        int appliedResources,
        List<IaCResource> resources
) {}
