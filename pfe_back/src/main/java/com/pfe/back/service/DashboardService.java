package com.pfe.back.service;

import com.pfe.back.dto.DashboardStats;
import com.pfe.back.entity.AlertSeverity;
import com.pfe.back.entity.ImageStatus;
import com.pfe.back.entity.PipelineStatus;
import com.pfe.back.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DockerImageRepository imageRepo;
    private final SecurityAlertRepository alertRepo;
    private final PipelineRunRepository pipelineRepo;
    private final DeploymentRepository deploymentRepo;

    public DashboardStats getStats() {
        long safe = imageRepo.countByStatus(ImageStatus.SAFE);
        long unsafe = imageRepo.countByStatus(ImageStatus.UNSAFE);

        Map<String, Long> sev = new LinkedHashMap<>();
        for (AlertSeverity s : AlertSeverity.values()) {
            sev.put(s.name(), alertRepo.countBySeverity(s));
        }

        return new DashboardStats(
                safe + unsafe,
                safe,
                unsafe,
                alertRepo.countByAcknowledgedFalse(),
                pipelineRepo.countByStatus(PipelineStatus.SUCCESS),
                pipelineRepo.countByStatus(PipelineStatus.FAILED),
                deploymentRepo.count(),
                sev
        );
    }
}
