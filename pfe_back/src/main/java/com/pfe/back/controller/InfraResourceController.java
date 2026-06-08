package com.pfe.back.controller;

import com.pfe.back.infra.dto.AzureResourceDto;
import com.pfe.back.infra.entity.AzureResourceEntity;
import com.pfe.back.infra.entity.AzureResourceHistoryEntity;
import com.pfe.back.infra.entity.SyncRunEntity;
import com.pfe.back.infra.repository.AzureResourceHistoryRepository;
import com.pfe.back.infra.repository.AzureResourceRepository;
import com.pfe.back.infra.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/infra")
@RequiredArgsConstructor
public class InfraResourceController {

    private final AzureResourceRepository resourceRepo;
    private final AzureResourceHistoryRepository historyRepo;
    private final SyncRunRepository syncRunRepo;

    @GetMapping("/resources")
    public Map<String, Object> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String rg,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "lastChangedAt"));
        Page<AzureResourceEntity> p = resourceRepo.search(type, rg, state, q, pageable);
        return Map.of(
                "content", p.getContent().stream().map(AzureResourceDto::from).toList(),
                "page", p.getNumber(),
                "size", p.getSize(),
                "total", p.getTotalElements());
    }

    @GetMapping("/resources/{id}")
    public ResponseEntity<AzureResourceDto> get(@PathVariable Long id) {
        return resourceRepo.findById(id)
                .map(AzureResourceDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/resources/{id}/history")
    public List<AzureResourceHistoryEntity> history(@PathVariable Long id) {
        return resourceRepo.findById(id)
                .map(r -> historyRepo.findByAzureIdOrderByOccurredAtDesc(r.getAzureId()))
                .orElse(List.of());
    }

    @GetMapping("/sync/runs")
    public List<SyncRunEntity> runs() {
        return syncRunRepo.findAll(Sort.by(Sort.Direction.DESC, "startedAt"))
                .stream().limit(50).toList();
    }
}
