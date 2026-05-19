package com.pfe.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.back.dto.IaCResource;
import com.pfe.back.dto.IaCSummary;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads the Terraform resource manifest (shipped on the backend classpath as
 * {@code iac/resources.json}) and optionally enriches each entry with state
 * from {@code infra/terraform.tfstate} on disk.
 *
 * The web UI consumes the result so a `terraform apply` is NOT required to
 * see the planned infrastructure — the manifest is enough.
 */
@Service
public class IaCService {

    private static final Logger log = LoggerFactory.getLogger(IaCService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String project;
    private String provider;
    private String location;
    private List<IaCResource> resources = List.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("iac/resources.json").getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            this.project  = text(root, "project");
            this.provider = text(root, "provider");
            this.location = text(root, "location");

            List<IaCResource> list = new ArrayList<>();
            for (JsonNode n : root.path("resources")) {
                Map<String, String> props = new LinkedHashMap<>();
                JsonNode p = n.path("properties");
                if (p.isObject()) {
                    p.fields().forEachRemaining(e -> props.put(e.getKey(), e.getValue().asText("")));
                }
                list.add(new IaCResource(
                        text(n, "address"),
                        text(n, "name"),
                        text(n, "type"),
                        text(n, "category"),
                        text(n, "icon"),
                        text(n, "description"),
                        "PLANNED",
                        props
                ));
            }
            this.resources = List.copyOf(list);
            log.info("IaC manifest loaded: {} resources ({} provider, project={})",
                    list.size(), provider, project);
        } catch (Exception e) {
            log.warn("Failed to load IaC manifest: {}", e.getMessage());
            this.project = "unknown";
            this.provider = "unknown";
            this.location = "unknown";
            this.resources = List.of();
        }
    }

    /** Resource manifest + status enrichment from a local tfstate if present. */
    public IaCSummary getSummary() {
        Set<String> applied = readAppliedAddresses();

        List<IaCResource> out = new ArrayList<>(resources.size());
        int appliedCount = 0;
        for (IaCResource r : resources) {
            boolean isApplied = applied.contains(r.address());
            if (isApplied) appliedCount++;
            out.add(IaCResource.withStatus(r, isApplied ? "APPLIED" : "PLANNED"));
        }
        return new IaCSummary(project, provider, location,
                !applied.isEmpty(), out.size(), appliedCount, out);
    }

    /** Parse Terraform state for the list of `<type>.<name>` addresses currently managed. */
    private Set<String> readAppliedAddresses() {
        // Search a few likely locations relative to the working directory.
        List<Path> candidates = List.of(
                Path.of("infra", "terraform.tfstate"),
                Path.of("..", "infra", "terraform.tfstate"),
                Path.of("/workspace/infra/terraform.tfstate")
        );
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            try (InputStream in = Files.newInputStream(p)) {
                JsonNode root = MAPPER.readTree(in);
                Set<String> addrs = new HashSet<>();
                for (JsonNode r : root.path("resources")) {
                    String type = r.path("type").asText("");
                    String name = r.path("name").asText("");
                    if (!type.isEmpty() && !name.isEmpty()) addrs.add(type + "." + name);
                }
                if (!addrs.isEmpty()) {
                    log.debug("Loaded {} applied addresses from {}", addrs.size(), p);
                    return addrs;
                }
            } catch (Exception e) {
                log.warn("Failed to read tfstate {}: {}", p, e.getMessage());
            }
        }
        return Set.of();
    }

    private static String text(JsonNode n, String f) {
        return n.has(f) && !n.get(f).isNull() ? n.get(f).asText() : null;
    }
}
