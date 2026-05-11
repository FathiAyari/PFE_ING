package com.pfe.back.service;

import com.pfe.back.dto.DeployRequest;
import com.pfe.back.entity.*;
import com.pfe.back.repository.DeploymentRepository;
import com.pfe.back.repository.DockerImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DockerImageRepository imageRepository;
    private final AuditLogService auditLogService;

    public List<Deployment> all() {
        return deploymentRepository.findAllByOrderByDeployedAtDesc();
    }

    @Transactional
    public Deployment deploy(Long imageId, DeployRequest req) {
        DockerImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        String target = image.getName() + ":" + image.getTag();

        if (image.getStatus() != ImageStatus.SAFE) {
            auditLogService.log("DEPLOY_IMAGE", req.triggeredBy(), target,
                    "Blocked deployment of UNSAFE image to " + req.environment(), "DENIED");
            throw new IllegalStateException("Only SAFE images can be deployed");
        }

        Deployment d = Deployment.builder()
                .image(image)
                .environment(req.environment())
                .status("SUCCESS")
                .triggeredBy(req.triggeredBy())
                .notes(req.notes())
                .deployedAt(Instant.now())
                .build();
        Deployment saved = deploymentRepository.save(d);

        auditLogService.log("DEPLOY_IMAGE", req.triggeredBy(), target,
                "Deployed to " + req.environment(), "OK");
        return saved;
    }
}
