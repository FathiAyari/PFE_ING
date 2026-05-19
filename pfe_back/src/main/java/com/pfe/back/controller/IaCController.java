package com.pfe.back.controller;

import com.pfe.back.dto.IaCSummary;
import com.pfe.back.service.IaCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iac")
@RequiredArgsConstructor
public class IaCController {

    private final IaCService iac;

    /** Manifest + status enrichment from local terraform.tfstate (if present). */
    @GetMapping("/resources")
    public IaCSummary resources() {
        return iac.getSummary();
    }
}
