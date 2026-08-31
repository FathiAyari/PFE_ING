package com.pfe.back.entity;

/** Lifecycle of an onboarding request. Provisioning is simulated (no real Terraform run). */
public enum ApplicationStatus {
    PENDING,        // developer submitted, awaiting admin review
    PROVISIONING,   // admin approved, resources being registered
    READY,          // resources registered, integration credentials issued
    REJECTED        // admin rejected the request
}

