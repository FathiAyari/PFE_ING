package com.pfe.back.infra.dto;

import com.pfe.back.infra.entity.AzureResourceHistoryEntity.ChangeType;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Pushed over STOMP /topic/resources whenever a resource is upserted/deleted. */
@Data
@AllArgsConstructor
public class ResourceChangeMessage {
    private ChangeType changeType;
    private AzureResourceDto resource;
}
