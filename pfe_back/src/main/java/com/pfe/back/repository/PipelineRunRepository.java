package com.pfe.back.repository;

import com.pfe.back.entity.PipelineRun;
import com.pfe.back.entity.PipelineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
    List<PipelineRun> findTop50ByOrderByStartedAtDesc();
    long countByStatus(PipelineStatus status);
}
