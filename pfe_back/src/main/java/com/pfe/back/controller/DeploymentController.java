package com.pfe.back.controller;

import com.pfe.back.dto.DeployRequest;
import com.pfe.back.entity.Deployment;
import com.pfe.back.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService service;

    @GetMapping
    public List<Deployment> all() { return service.all(); }

    @PostMapping("/images/{imageId}")
    public ResponseEntity<?> deploy(@PathVariable Long imageId, @Valid @RequestBody DeployRequest req) {
        try {
            return ResponseEntity.ok(service.deploy(imageId, req));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
