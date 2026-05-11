package com.pfe.back.dto;

import java.util.Map;

public record DashboardStats(
        long totalImages,
        long safeImages,
        long unsafeImages,
        long openAlerts,
        long pipelinesSuccess,
        long pipelinesFailed,
        long deployments,
        Map<String, Long> alertsBySeverity
) {}
