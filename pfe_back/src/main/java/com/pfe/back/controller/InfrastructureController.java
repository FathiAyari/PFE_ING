package com.pfe.back.controller;

import com.pfe.back.entity.SystemNode;
import com.pfe.back.repository.SystemNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/infrastructure")
@RequiredArgsConstructor
public class InfrastructureController {

    private final SystemNodeRepository repo;

    @GetMapping("/nodes")
    public List<SystemNode> nodes() { return repo.findAll(); }

    @GetMapping("/health")
    public Object health() {
        long up = repo.findAll().stream().filter(n -> "UP".equals(n.getStatus())).count();
        long total = repo.count();
        return java.util.Map.of(
                "up", up,
                "total", total,
                "healthy", up == total
        );
    }
}
