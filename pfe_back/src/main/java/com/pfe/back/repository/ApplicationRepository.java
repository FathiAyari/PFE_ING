package com.pfe.back.repository;

import com.pfe.back.entity.Application;
import com.pfe.back.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findAllByOrderByCreatedAtDesc();
    List<Application> findByStatusOrderByCreatedAtDesc(ApplicationStatus status);
    long countByStatus(ApplicationStatus status);
    boolean existsByNameIgnoreCase(String name);
}

