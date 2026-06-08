package com.pfe.back.infra.repository;

import com.pfe.back.infra.entity.AzureResourceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AzureResourceHistoryRepository extends JpaRepository<AzureResourceHistoryEntity, Long> {
    List<AzureResourceHistoryEntity> findByAzureIdOrderByOccurredAtDesc(String azureId);
}
