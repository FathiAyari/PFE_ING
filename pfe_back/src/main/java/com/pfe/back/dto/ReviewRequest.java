package com.pfe.back.dto;

/** Optional reason supplied when an admin rejects or approves a request. */
public record ReviewRequest(
        String actor,
        String reason
) {}

