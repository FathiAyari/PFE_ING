package com.pfe.back.service;

import com.pfe.back.entity.AuditLog;
import com.pfe.back.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;

    public AuditLog log(String action, String actor, String target, String details, String result) {
        return repo.save(AuditLog.builder()
                .action(action)
                .actor(actor)
                .target(target)
                .details(details)
                .result(result)
                .timestamp(Instant.now())
                .build());
    }

    public List<AuditLog> recent() {
        return repo.findTop200ByOrderByTimestampDesc();
    }
}
