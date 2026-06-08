package com.pfe.back.infra.repository;

import com.pfe.back.infra.entity.ProcessedEventEntity;
import com.pfe.back.infra.entity.VmStateEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VmStateEventRepository extends JpaRepository<VmStateEventEntity, Long> {}
