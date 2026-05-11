package com.pfe.back.controller;

import com.pfe.back.entity.SecurityAlert;
import com.pfe.back.repository.SecurityAlertRepository;
import com.pfe.back.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class SecurityAlertController {

    private final SecurityAlertRepository repo;
    private final AuditLogService audit;

    @GetMapping
    public List<SecurityAlert> all() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/open")
    public List<SecurityAlert> open() {
        return repo.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    @PostMapping("/{id}/acknowledge")
    public SecurityAlert acknowledge(@PathVariable Long id,
                                     @RequestParam(defaultValue = "admin") String actor) {
        SecurityAlert a = repo.findById(id).orElseThrow();
        a.setAcknowledged(true);
        SecurityAlert saved = repo.save(a);
        audit.log("ACK_ALERT", actor, "alert#" + id, a.getTitle(), "OK");
        return saved;
    }
}
