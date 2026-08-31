package com.pfe.back.service;

import com.pfe.back.dto.ApplicationRequest;
import com.pfe.back.entity.*;
import com.pfe.back.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
wimport org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Onboarding workflow for applications. Provisioning is <b>simulated</b>:
 * approving a request derives the Azure resource names from the app name and
 * registers them as {@link ApplicationResource} rows (no Terraform run), then
 * issues integration credentials and marks the app READY.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository repo;
    private final AuditLogService auditLogService;
    private final MailService mailService;

    @Value("${app.public-url:http://localhost:4200}")
    private String publicUrl;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public List<Application> all() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public List<Application> byStatus(ApplicationStatus status) {
        return repo.findByStatusOrderByCreatedAtDesc(status);
    }

    public Application get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }

    /** Developer submits an onboarding request. Starts in PENDING. */
    @Transactional
    public Application submit(ApplicationRequest req, String actor) {
        if (repo.existsByNameIgnoreCase(req.name().trim())) {
            throw new IllegalArgumentException("An application named '" + req.name() + "' already exists");
        }
        Application app = Application.builder()
                .name(req.name().trim())
                .description(req.description())
                .team(req.team())
                .repositoryUrl(req.repositoryUrl())
                .contactEmail(req.contactEmail())
                .deploymentType(req.deploymentType())
                .database(req.database())
                .needsContainerRegistry(req.needsContainerRegistry())
                .needsKeyVault(req.needsKeyVault())
                .environment(req.environment())
                .status(ApplicationStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        Application saved = repo.save(app);

        auditLogService.log("APP_ONBOARD_REQUEST", actorOr(actor, "developer"),
                saved.getName(), "Onboarding request submitted", "OK");
        return saved;
    }

    /**
     * Admin approves a request. Registers the derived Azure resources (simulated
     * Terraform outputs), issues integration credentials, marks READY.
     */
    @Transactional
    public Application approve(Long id, String actor) {
        Application app = get(id);
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved (current: " + app.getStatus() + ")");
        }

        app.setStatus(ApplicationStatus.PROVISIONING);

        String slug = slug(app.getName());
        List<ApplicationResource> resources = deriveResources(app, slug);
        app.getResources().clear();
        app.getResources().addAll(resources);

        app.setApplicationCode(nextApplicationCode());
        app.setIntegrationToken("pfe_" + randomToken(28));
        app.setStatus(ApplicationStatus.READY);
        app.setDecidedAt(Instant.now());
        app.setDecidedBy(actorOr(actor, "admin"));

        Application saved = repo.save(app);

        auditLogService.log("APP_ONBOARD_APPROVE", saved.getDecidedBy(), saved.getName(),
                "Approved. Registered " + resources.size() + " Azure resources, issued token "
                        + saved.getApplicationCode(), "OK");

        sendApprovalEmail(saved);
        return saved;
    }

    /** Admin rejects a request. */
    @Transactional
    public Application reject(Long id, String actor, String reason) {
        Application app = get(id);
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected (current: " + app.getStatus() + ")");
        }
        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        app.setDecidedAt(Instant.now());
        app.setDecidedBy(actorOr(actor, "admin"));
        Application saved = repo.save(app);

        auditLogService.log("APP_ONBOARD_REJECT", saved.getDecidedBy(), saved.getName(),
                "Rejected" + (reason != null && !reason.isBlank() ? ": " + reason : ""), "DENIED");
        return saved;
    }

    // ---- helpers ----

    /** Notifies the developer their application is READY, with integration details (no Azure secrets). */
    private void sendApprovalEmail(Application app) {
        String resourceLines = app.getResources().stream()
                .map(r -> "  - " + r.getResourceType() + ": " + r.getIdentifier())
                .collect(Collectors.joining("\n"));

        String subject = "[PFE] Your application \"" + app.getName() + "\" is ready (" + app.getApplicationCode() + ")";
        String body = """
                Hi %s team,

                Good news — your onboarding request for "%s" has been approved and provisioned.

                Application ID : %s
                Integration Token : %s

                Portal: %s

                Registered resources:
                %s

                Quick integration guide
                ------------------------
                1. Store the Integration Token securely (e.g. your CI secret store). It authenticates
                   your app with the PFE platform — treat it like a password and never commit it.
                2. Send it as the header:  X-PFE-Token: %s
                3. Open the portal above to view your application, resources and security posture.

                Note: this email does NOT contain any Azure credentials. Cloud access is managed
                separately by the platform team.

                — PFE DevSecOps Platform
                """.formatted(
                        nvl(app.getTeam(), app.getName()),
                        app.getName(),
                        app.getApplicationCode(),
                        app.getIntegrationToken(),
                        publicUrl,
                        resourceLines.isBlank() ? "  (none)" : resourceLines,
                        app.getIntegrationToken());

        mailService.send(app.getContactEmail(), subject, body);
    }

    /** Simulated Terraform outputs: derive resource names from the app slug. */
    private List<ApplicationResource> deriveResources(Application app, String slug) {
        List<ApplicationResource> list = new ArrayList<>();
        list.add(resource(app, "RESOURCE_GROUP", slug + "-rg"));

        if (app.isNeedsContainerRegistry()) {
            list.add(resource(app, "CONTAINER_REGISTRY", slug + "acr.azurecr.io"));
        }
        switch (app.getDeploymentType()) {
            case VM -> list.add(resource(app, "VM", slug + "-vm"));
            case CONTAINER_APP -> list.add(resource(app, "CONTAINER_APP", slug + "-api"));
            case AKS -> list.add(resource(app, "AKS", slug + "-aks"));
        }
        if (app.getDatabase() == DatabaseType.POSTGRESQL) {
            list.add(resource(app, "POSTGRESQL", slug + "-db"));
        }
        if (app.isNeedsKeyVault()) {
            list.add(resource(app, "KEY_VAULT", slug + "-kv"));
        }
        return list;
    }

    private ApplicationResource resource(Application app, String type, String identifier) {
        return ApplicationResource.builder()
                .application(app)
                .resourceType(type)
                .identifier(identifier)
                .build();
    }

    /** APP-001, APP-002, ... based on how many apps exist. */
    private String nextApplicationCode() {
        long n = repo.count();
        return String.format("APP-%03d", n);
    }

    private String slug(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return s.isEmpty() ? "app" : s;
    }

    private String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String actorOr(String actor, String fallback) {
        return (actor == null || actor.isBlank()) ? fallback : actor;
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}

