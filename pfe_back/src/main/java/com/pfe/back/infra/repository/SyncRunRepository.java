package com.pfe.back.infra.repository;

import com.pfe.back.infra.entity.SyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncRunRepository extends JpaRepository<SyncRunEntity, Long> {
}
