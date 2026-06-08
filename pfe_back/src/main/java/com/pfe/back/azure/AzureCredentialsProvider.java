package com.pfe.back.azure;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ClientSecretCredential} + {@link AzureProfile} reused by
 * every Azure SDK client. Fails soft when {@link AzureProperties#isConfigured()}
 * is {@code false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Getter
public class AzureCredentialsProvider {

    private final AzureProperties props;

    private ClientSecretCredential credential;
    private AzureProfile profile;

    @PostConstruct
    void init() {
        if (!props.isConfigured()) {
            log.warn("Azure credentials not configured (set APP_AZURE_* env vars). "
                    + "Real-time infra sync is disabled.");
            return;
        }
        this.credential = new ClientSecretCredentialBuilder()
                .tenantId(props.getTenantId())
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .build();
        this.profile = new AzureProfile(props.getTenantId(),
                props.getSubscriptionId(),
                AzureEnvironment.AZURE);
        log.info("Azure SDK credentials initialised for subscription {}",
                props.getSubscriptionId());
    }

    public boolean isReady() {
        return credential != null && profile != null;
    }
}
