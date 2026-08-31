package com.pfe.back.controller;

import com.pfe.back.dto.ApplicationRequest;
import com.pfe.back.dto.ReviewRequest;
import com.pfe.back.entity.Application;
import com.pfe.back.entity.ApplicationStatus;
import com.pfe.back.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Application onboarding endpoints. Developers submit requests; admins review
 * (approve/reject). Approval registers derived Azure resources and issues
 * integration credentials (provisioning is simulated — no Terraform run).
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;

    @GetMapping
    public List<Application> all(@RequestParam(required = false) ApplicationStatus status) {
        return status == null ? service.all() : service.byStatus(status);
    }

    @GetMapping("/pending")
    public List<Application> pending() {
        return service.byStatus(ApplicationStatus.PENDING);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.get(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody ApplicationRequest req, Authentication auth) {
        try {
            return ResponseEntity.ok(service.submit(req, actor(auth)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body,
                                     Authentication auth) {
        try {
            return ResponseEntity.ok(service.approve(id, reviewActor(body, auth)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body,
                                    Authentication auth) {
        try {
            String reason = body != null ? body.reason() : null;
            return ResponseEntity.ok(service.reject(id, reviewActor(body, auth), reason));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    private String actor(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private String reviewActor(ReviewRequest body, Authentication auth) {
        if (body != null && body.actor() != null && !body.actor().isBlank()) return body.actor();
        return actor(auth);
    }
}

