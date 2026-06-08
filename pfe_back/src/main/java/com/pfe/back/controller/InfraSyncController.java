package com.pfe.back.controller;

import com.pfe.back.infra.entity.SyncRunEntity;
import com.pfe.back.infra.entity.SyncRunEntity.Kind;
import com.pfe.back.infra.service.ResourceSyncService;
import com.pfe.back.infra.util.HmacVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Called by GitHub Actions after a successful Terraform apply. Validates the
 * HMAC signature, then triggers a full Resource Graph sweep so the new
 * resources are persisted and broadcast immediately.
 */
@RestController
@RequestMapping("/api/infra/sync")
@RequiredArgsConstructor
@Slf4j
public class InfraSyncController {

    private final ResourceSyncService syncService;

    @Value("${app.infra.webhook-secret}")
    private String secret;

    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody) {
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        if (!HmacVerifier.verify(body, signature, secret)) {
            log.warn("Rejected /api/infra/sync/trigger: invalid HMAC");
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }
        SyncRunEntity run = syncService.runFullSync(null, Kind.TERRAFORM_HOOK);
        return ResponseEntity.accepted().body(Map.of(
                "runId", run.getId(),
                "status", run.getStatus(),
                "resourcesSeen", run.getResourcesSeen(),
                "resourcesChanged", run.getResourcesChanged()));
    }
}
