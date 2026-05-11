package com.pfe.back.repository;

import com.pfe.back.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findAllByOrderByDeployedAtDesc();
}
