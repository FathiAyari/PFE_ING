package com.pfe.back.dto;

import java.util.List;
import java.util.Map;

/** One resource as rendered on the /infra page. */
public record IaCResource(
        String address,        // azurerm_linux_virtual_machine.vm
        String name,           // pfe-devsecops-vm
        String type,           // "Linux VM"
        String category,       // compute | network | security | registry
        String icon,           // emoji used as a small badge
        String description,
        String status,         // PLANNED | APPLIED
        Map<String, String> properties
) {
    public static IaCResource withStatus(IaCResource r, String status) {
        return new IaCResource(r.address(), r.name(), r.type(), r.category(),
                r.icon(), r.description(), status, r.properties());
    }
}
