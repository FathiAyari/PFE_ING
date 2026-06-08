package com.pfe.back.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.back.infra.entity.ProcessedEventEntity;
import com.pfe.back.infra.entity.SyncRunEntity.Kind;
import com.pfe.back.infra.repository.ProcessedEventRepository;
import com.pfe.back.infra.service.ResourceSyncService;
import com.pfe.back.infra.service.ResourceUpsertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Receives Azure Event Grid notifications for resource changes.
 * Handles the EG validation handshake on first subscription.
 */
@RestController
@RequestMapping("/api/azure")
@RequiredArgsConstructor
@Slf4j
public class EventGridWebhookController {

    private final ResourceSyncService syncService;
    private final ResourceUpsertService upsertService;
    private final ProcessedEventRepository processedRepo;

    @PostMapping("/events")
    public ResponseEntity<?> events(@RequestBody JsonNode body,
                                    @RequestHeader(value = "aeg-event-type", required = false) String aegType) {
        // Single event or array
        List<JsonNode> events = body.isArray()
                ? List.of(body).stream().flatMap(n -> java.util.stream.StreamSupport.stream(n.spliterator(), false)).toList()
                : List.of(body);

        // Subscription validation handshake (legacy schema)
        for (JsonNode ev : events) {
            String eventType = text(ev, "eventType");
            if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
                String code = ev.path("data").path("validationCode").asText();
                log.info("Event Grid subscription validation handshake");
                return ResponseEntity.ok(Map.of("validationResponse", code));
            }
        }

        for (JsonNode ev : events) {
            String id = text(ev, "id");
            if (id != null && processedRepo.existsById(id)) continue; // dedupe
            String eventType = text(ev, "eventType");
            String subject = text(ev, "subject");
            log.debug("EG event {} {} {}", id, eventType, subject);

            if (eventType != null && eventType.endsWith("ResourceDeleteSuccess")) {
                if (subject != null) upsertService.markDeleted(subject, "EVENT_GRID", id);
            } else {
                // For Write/Action events do a quick targeted sweep to refresh state.
                // Cheaper than per-id Resource Graph calls and reuses one code path.
                syncService.runFullSync(id, Kind.EVENT_GRID);
            }
            if (id != null) {
                try {
                    processedRepo.save(new ProcessedEventEntity(id, Instant.now()));
                } catch (Exception ignore) { /* race-safe */ }
            }
        }
        return ResponseEntity.ok().build();
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }
}
