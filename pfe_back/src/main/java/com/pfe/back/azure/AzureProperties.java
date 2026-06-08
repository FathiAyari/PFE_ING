package com.pfe.back.azure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound from {@code app.azure.*} properties / {@code APP_AZURE_*} env vars.
 * If {@link #isConfigured()} returns false the Azure SDK layer stays
 * dormant and the app runs in DB-only mode (useful for local dev).
 */
@Component
@ConfigurationProperties(prefix = "app.azure")
@Getter
@Setter
public class AzureProperties {

    private String tenantId = "";
    private String clientId = "";
    private String clientSecret = "";
    private String subscriptionId = "";
    /** Optional. When set, scopes Resource Graph queries to this RG. */
    private String resourceGroup = "";

    public boolean isConfigured() {
        return !tenantId.isBlank()
                && !clientId.isBlank()
                && !clientSecret.isBlank()
                && !subscriptionId.isBlank();
    }
}
