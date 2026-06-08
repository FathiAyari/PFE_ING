package com.pfe.back.azure;

import com.azure.resourcemanager.resourcegraph.ResourceGraphManager;
import com.azure.resourcemanager.resourcegraph.models.QueryRequest;
import com.azure.resourcemanager.resourcegraph.models.QueryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper around Azure Resource Graph (Kusto) used for periodic
 * inventory sweeps and event-driven by-id lookups.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AzureResourceGraphClient {

    private final AzureCredentialsProvider credentials;
    private final AzureProperties props;
    private final ObjectMapper objectMapper;

    private ResourceGraphManager manager;

    @PostConstruct
    void init() {
        if (!credentials.isReady()) return;
        try {
            this.manager = ResourceGraphManager.authenticate(
                    credentials.getCredential(), credentials.getProfile());
            log.info("Resource Graph client ready");
        } catch (Exception e) {
            log.error("Failed to init Resource Graph client: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        return manager != null;
    }

    /**
     * Returns all resources in the configured subscription (optionally
     * filtered to a single RG). Each element is a JsonNode with at least
     * {@code id, name, type, kind, location, resourceGroup, subscriptionId,
     * tags, properties, sku}.
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<JsonNode> listAllResources() {
        if (!isReady()) return Collections.emptyList();

        String rgFilter = props.getResourceGroup() == null || props.getResourceGroup().isBlank()
                ? ""
                : " | where resourceGroup =~ '" + props.getResourceGroup() + "'";

        String kusto = "Resources"
                + rgFilter
                + " | project id, name, type, kind, location, resourceGroup,"
                + " subscriptionId, tags, properties, sku"
                + " | limit 1000";

        QueryRequest request = new QueryRequest()
                .withSubscriptions(List.of(props.getSubscriptionId()))
                .withQuery(kusto);

        QueryResponse resp = manager.resourceProviders().resources(request);
        Object data = resp.data();
        if (data == null) return Collections.emptyList();
        try {
            JsonNode node = objectMapper.valueToTree(data);
            // Resource Graph returns either an array or {columns, rows}.
            // The Java SDK normalises to array of objects when project is used.
            if (node.isArray()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, JsonNode.class));
            }
            // Fallback: rows/columns shape
            JsonNode rows = node.get("rows");
            JsonNode cols = node.get("columns");
            if (rows == null || cols == null) return Collections.emptyList();
            return rowsToObjects(cols, rows);
        } catch (Exception e) {
            log.error("Failed to parse Resource Graph response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<JsonNode> rowsToObjects(JsonNode cols, JsonNode rows) {
        var out = new java.util.ArrayList<JsonNode>(rows.size());
        for (JsonNode row : rows) {
            var obj = objectMapper.createObjectNode();
            for (int i = 0; i < cols.size() && i < row.size(); i++) {
                String name = cols.get(i).get("name").asText();
                obj.set(name, row.get(i));
            }
            out.add(obj);
        }
        return out;
    }
}
