package com.pfe.back.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link TokenCredential} + {@link AzureProfile} reused by every
 * Azure SDK client. Two authentication modes, resolved automatically at boot:
 *
 * <ol>
 *   <li><b>Explicit service principal</b> — when BOTH {@code APP_AZURE_CLIENT_ID}
 *       and {@code APP_AZURE_CLIENT_SECRET} are set. Used by Docker, CI, and
 *       any environment where you inject creds as env vars.</li>
 *   <li><b>{@link DefaultAzureCredentialBuilder} chain</b> — when they are
 *       absent. Falls through: env vars → workload identity → managed identity
 *       → IDE (VS Code / IntelliJ Azure plugin) → {@code az login} → Azure
 *       PowerShell. This is what a developer laptop uses: run
 *       {@code az login} once and the backend authenticates as YOU, no
 *       secrets stored on disk.</li>
 * </ol>
 *
 * Requires only {@code APP_AZURE_TENANT_ID} + {@code APP_AZURE_SUBSCRIPTION_ID}
 * to know which tenant/subscription to target. When those are blank the whole
 * Azure layer stays dormant (DB-only mode for pure UI work).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Getter
public class AzureCredentialsProvider {

    private final AzureProperties props;

    private TokenCredential credential;
    private AzureProfile profile;

    @PostConstruct
    void init() {
        String tenant = nullIfBlank(props.getTenantId());
        String sub    = nullIfBlank(props.getSubscriptionId());
        if (tenant == null || sub == null) {
            log.warn("Azure not configured: set APP_AZURE_TENANT_ID and "
                    + "APP_AZURE_SUBSCRIPTION_ID. Real-time infra sync is disabled.");
            return;
        }

        String clientId     = nullIfBlank(props.getClientId());
        String clientSecret = nullIfBlank(props.getClientSecret());

        if (clientId != null && clientSecret != null) {
            this.credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenant)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
            log.info("Azure auth: service principal clientId={}, subscription {}",
                    clientId, sub);
        } else {
            // DefaultAzureCredential chain — laptop uses `az login`, prod uses
            // Managed Identity / Workload Identity. No secrets on disk.
            this.credential = new DefaultAzureCredentialBuilder()
                    .tenantId(tenant)
                    .build();
            log.info("Azure auth: DefaultAzureCredential (az login / MI / IDE), "
                    + "tenant {}, subscription {}", tenant, sub);
        }

        this.profile = new AzureProfile(tenant, sub, AzureEnvironment.AZURE);
    }

    public boolean isReady() {
        return credential != null && profile != null;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
