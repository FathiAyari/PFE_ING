package com.pfe.back.config;

import com.pfe.back.entity.*;
import com.pfe.back.infra.entity.AzureResourceEntity;
import com.pfe.back.infra.entity.SyncRunEntity;
import com.pfe.back.repository.*;
import com.pfe.back.infra.repository.AzureResourceRepository;
import com.pfe.back.infra.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DockerImageRepository images;
    private final PipelineRunRepository pipelines;
    private final SecurityAlertRepository alerts;
    private final SystemNodeRepository nodes;
    private final AuditLogRepository audit;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AzureResourceRepository azureResources;
    private final SyncRunRepository syncRuns;
    private final ApplicationRepository applications;

    @Override
    public void run(String... args) {
        // ---- Default cloud admin user (always ensured) ----
        if (!users.existsByUsername("admin")) {
            users.save(User.builder()
                    .username("admin")
                    .passwordHash(encoder.encode("admin123"))
                    .email("admin@pfe.local")
                    .role(Role.CLOUD_ADMIN)
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build());
        }

        // ---- Sample onboarding requests (idempotent, independent of images) ----
        if (applications.count() == 0) {
            Application ready = Application.builder()
                    .name("PeopleOps")
                    .description("HR management platform (employees, leave, payroll). Stores PII.")
                    .team("HR Platform Squad")
                    .repositoryUrl("github.com/sopra-hr/peopleops")
                    .contactEmail("peopleops-lead@sopra-hr.com")
                    .deploymentType(DeploymentType.CONTAINER_APP)
                    .database(DatabaseType.POSTGRESQL)
                    .needsContainerRegistry(true)
                    .needsKeyVault(true)
                    .environment(AppEnvironment.PRODUCTION)
                    .status(ApplicationStatus.READY)
                    .applicationCode("APP-001")
                    .integrationToken("pfe_seededPeopleOpsTokenExample01")
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .decidedAt(Instant.now().minus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                    .decidedBy("admin")
                    .build();
            ready.getResources().add(res(ready, "RESOURCE_GROUP", "peopleops-rg"));
            ready.getResources().add(res(ready, "CONTAINER_REGISTRY", "peopleopsacr.azurecr.io"));
            ready.getResources().add(res(ready, "CONTAINER_APP", "peopleops-api"));
            ready.getResources().add(res(ready, "POSTGRESQL", "peopleops-db"));
            ready.getResources().add(res(ready, "KEY_VAULT", "peopleops-kv"));

            Application pending = Application.builder()
                    .name("RecruitPro")
                    .description("Applicant tracking system for recruitment.")
                    .team("Talent Acquisition")
                    .repositoryUrl("github.com/sopra-hr/recruitpro")
                    .contactEmail("recruitpro-lead@sopra-hr.com")
                    .deploymentType(DeploymentType.AKS)
                    .database(DatabaseType.POSTGRESQL)
                    .needsContainerRegistry(true)
                    .needsKeyVault(true)
                    .environment(AppEnvironment.DEVELOPMENT)
                    .status(ApplicationStatus.PENDING)
                    .createdAt(Instant.now().minus(3, ChronoUnit.HOURS))
                    .build();

            applications.saveAll(List.of(ready, pending));
        }


        if (images.count() > 0) return;
        Instant now = Instant.now();

        // ---- Docker images ----
        images.saveAll(List.of(
            DockerImage.builder().name("nginx").tag("1.27-alpine").registry("docker.io")
                .status(ImageStatus.SAFE).vulnerabilityCount(0).riskScore(0.5)
                .scannedAt(now.minus(2, ChronoUnit.HOURS))
                .digest("sha256:a1b2c3").sizeBytes(48_000_000L).build(),
            DockerImage.builder().name("pfe-backend").tag("1.0.3").registry("ghcr.io/pfe")
                .status(ImageStatus.SAFE).vulnerabilityCount(1).riskScore(1.8)
                .scannedAt(now.minus(45, ChronoUnit.MINUTES))
                .digest("sha256:d4e5f6").sizeBytes(220_000_000L).build(),
            DockerImage.builder().name("pfe-frontend").tag("1.0.2").registry("ghcr.io/pfe")
                .status(ImageStatus.SAFE).vulnerabilityCount(0).riskScore(0.2)
                .scannedAt(now.minus(30, ChronoUnit.MINUTES))
                .digest("sha256:7g8h9i").sizeBytes(90_000_000L).build(),
            DockerImage.builder().name("redis").tag("7.2").registry("docker.io")
                .status(ImageStatus.SAFE).vulnerabilityCount(0).riskScore(0.7)
                .scannedAt(now.minus(5, ChronoUnit.HOURS))
                .digest("sha256:r3d15a").sizeBytes(120_000_000L).build(),
            DockerImage.builder().name("legacy-app").tag("0.9.1").registry("docker.io")
                .status(ImageStatus.UNSAFE).reason("CVE-2024-23897 (Critical) - Jenkins arbitrary file read")
                .vulnerabilityCount(12).riskScore(9.8)
                .scannedAt(now.minus(1, ChronoUnit.HOURS))
                .digest("sha256:bad001").sizeBytes(310_000_000L).build(),
            DockerImage.builder().name("old-node").tag("12-buster").registry("docker.io")
                .status(ImageStatus.UNSAFE).reason("Outdated base image - 27 high CVEs (OpenSSL, glibc)")
                .vulnerabilityCount(27).riskScore(8.4)
                .scannedAt(now.minus(3, ChronoUnit.HOURS))
                .digest("sha256:bad002").sizeBytes(910_000_000L).build(),
            DockerImage.builder().name("vuln-php").tag("5.6").registry("docker.io")
                .status(ImageStatus.UNSAFE).reason("End-of-life PHP version, 41 known vulnerabilities")
                .vulnerabilityCount(41).riskScore(9.1)
                .scannedAt(now.minus(20, ChronoUnit.HOURS))
                .digest("sha256:bad003").sizeBytes(420_000_000L).build()
        ));

        // ---- Pipelines ----
        pipelines.saveAll(List.of(
            PipelineRun.builder().name("build-backend").branch("main").commitSha("a1b2c3d")
                .triggeredBy("ci-bot").status(PipelineStatus.SUCCESS)
                .startedAt(now.minus(2, ChronoUnit.HOURS)).finishedAt(now.minus(118, ChronoUnit.MINUTES))
                .durationSeconds(120L).build(),
            PipelineRun.builder().name("build-frontend").branch("main").commitSha("e4f5g6h")
                .triggeredBy("ci-bot").status(PipelineStatus.SUCCESS)
                .startedAt(now.minus(90, ChronoUnit.MINUTES)).finishedAt(now.minus(87, ChronoUnit.MINUTES))
                .durationSeconds(180L).build(),
            PipelineRun.builder().name("security-scan").branch("main").commitSha("e4f5g6h")
                .triggeredBy("ci-bot").status(PipelineStatus.FAILED)
                .startedAt(now.minus(85, ChronoUnit.MINUTES)).finishedAt(now.minus(80, ChronoUnit.MINUTES))
                .durationSeconds(300L).failureStage("trivy-scan").build(),
            PipelineRun.builder().name("deploy-staging").branch("main").commitSha("e4f5g6h")
                .triggeredBy("alice").status(PipelineStatus.SUCCESS)
                .startedAt(now.minus(60, ChronoUnit.MINUTES)).finishedAt(now.minus(55, ChronoUnit.MINUTES))
                .durationSeconds(300L).build(),
            PipelineRun.builder().name("build-backend").branch("feature/auth").commitSha("z9y8x7w")
                .triggeredBy("bob").status(PipelineStatus.RUNNING)
                .startedAt(now.minus(3, ChronoUnit.MINUTES)).build(),
            PipelineRun.builder().name("nightly-scan").branch("main").commitSha("a1b2c3d")
                .triggeredBy("scheduler").status(PipelineStatus.FAILED)
                .startedAt(now.minus(10, ChronoUnit.HOURS)).finishedAt(now.minus(595, ChronoUnit.MINUTES))
                .durationSeconds(300L).failureStage("dependency-check").build()
        ));

        // ---- Alerts ----
        alerts.saveAll(List.of(
            SecurityAlert.builder().title("Critical CVE in legacy-app:0.9.1")
                .description("CVE-2024-23897 allows arbitrary file read")
                .severity(AlertSeverity.CRITICAL).source("trivy").cveId("CVE-2024-23897")
                .affectedImage("legacy-app:0.9.1").acknowledged(false)
                .createdAt(now.minus(1, ChronoUnit.HOURS)).build(),
            SecurityAlert.builder().title("High vulnerabilities in old-node:12-buster")
                .description("Outdated OpenSSL and glibc")
                .severity(AlertSeverity.HIGH).source("trivy").cveId("CVE-2023-0464")
                .affectedImage("old-node:12-buster").acknowledged(false)
                .createdAt(now.minus(3, ChronoUnit.HOURS)).build(),
            SecurityAlert.builder().title("End-of-life PHP detected")
                .description("PHP 5.6 is no longer maintained")
                .severity(AlertSeverity.HIGH).source("snyk")
                .affectedImage("vuln-php:5.6").acknowledged(false)
                .createdAt(now.minus(20, ChronoUnit.HOURS)).build(),
            SecurityAlert.builder().title("Suspicious container behavior")
                .description("Falco: unexpected outbound connection from pfe-backend")
                .severity(AlertSeverity.MEDIUM).source("falco")
                .affectedImage("pfe-backend:1.0.3").acknowledged(true)
                .createdAt(now.minus(6, ChronoUnit.HOURS)).build(),
            SecurityAlert.builder().title("Image not signed")
                .description("redis:7.2 is missing Cosign signature")
                .severity(AlertSeverity.LOW).source("cosign")
                .affectedImage("redis:7.2").acknowledged(false)
                .createdAt(now.minus(8, ChronoUnit.HOURS)).build()
        ));

        // ---- System nodes ----
        nodes.saveAll(List.of(
            SystemNode.builder().name("vm-build-01").type("VM").status("UP")
                .cpuUsage(34.2).memoryUsage(58.1).diskUsage(42.0)
                .host("vm-build-01.lab").ipAddress("10.0.1.10").lastCheck(now).build(),
            SystemNode.builder().name("vm-deploy-01").type("VM").status("UP")
                .cpuUsage(21.4).memoryUsage(46.8).diskUsage(38.0)
                .host("vm-deploy-01.lab").ipAddress("10.0.1.20").lastCheck(now).build(),
            SystemNode.builder().name("registry").type("CONTAINER").status("UP")
                .cpuUsage(8.0).memoryUsage(25.0).diskUsage(60.0)
                .host("vm-build-01.lab").ipAddress("10.0.1.10").lastCheck(now).build(),
            SystemNode.builder().name("pfe-backend").type("BACKEND").status("UP")
                .cpuUsage(12.5).memoryUsage(33.4).diskUsage(15.0)
                .host("vm-deploy-01.lab").ipAddress("10.0.1.20").lastCheck(now).build(),
            SystemNode.builder().name("pfe-frontend").type("CONTAINER").status("UP")
                .cpuUsage(4.2).memoryUsage(18.1).diskUsage(10.0)
                .host("vm-deploy-01.lab").ipAddress("10.0.1.20").lastCheck(now).build(),
            SystemNode.builder().name("mysql-prod").type("DATABASE").status("DEGRADED")
                .cpuUsage(78.6).memoryUsage(82.3).diskUsage(74.0)
                .host("vm-db-01.lab").ipAddress("10.0.1.30").lastCheck(now).build(),
            SystemNode.builder().name("jenkins").type("CONTAINER").status("DOWN")
                .cpuUsage(0).memoryUsage(0).diskUsage(55.0)
                .host("vm-build-01.lab").ipAddress("10.0.1.10").lastCheck(now.minus(15, ChronoUnit.MINUTES)).build()
        ));

        // ---- Audit log seeds ----
        audit.saveAll(List.of(
            AuditLog.builder().action("SCAN_IMAGE").actor("scanner").target("legacy-app:0.9.1")
                .details("Detected 12 vulnerabilities").result("OK")
                .timestamp(now.minus(1, ChronoUnit.HOURS)).build(),
            AuditLog.builder().action("DEPLOY_IMAGE").actor("alice").target("pfe-backend:1.0.3")
                .details("Deployed to staging").result("OK")
                .timestamp(now.minus(55, ChronoUnit.MINUTES)).build(),
            AuditLog.builder().action("DEPLOY_IMAGE").actor("bob").target("legacy-app:0.9.1")
                .details("Blocked deployment of UNSAFE image to prod").result("DENIED")
                .timestamp(now.minus(40, ChronoUnit.MINUTES)).build()
        ));
    }

    private ApplicationResource res(Application app, String type, String identifier) {
        return ApplicationResource.builder()
                .application(app)
                .resourceType(type)
                .identifier(identifier)
                .build();
    }
}
