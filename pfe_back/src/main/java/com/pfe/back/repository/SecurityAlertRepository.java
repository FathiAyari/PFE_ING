package com.pfe.back.repository;

import com.pfe.back.entity.AlertSeverity;
import com.pfe.back.entity.SecurityAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {
    List<SecurityAlert> findAllByOrderByCreatedAtDesc();
    List<SecurityAlert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    long countBySeverity(AlertSeverity severity);
    long countByAcknowledgedFalse();
}
