package com.pfe.back.infra.repository;

import com.pfe.back.infra.entity.AzureResourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AzureResourceRepository extends JpaRepository<AzureResourceEntity, Long> {

    Optional<AzureResourceEntity> findByAzureId(String azureId);

    List<AzureResourceEntity> findAllByDeletedAtIsNull();

    @Query("select r from AzureResourceEntity r where "
            + "(:type is null or r.type = :type) and "
            + "(:rg is null or r.resourceGroup = :rg) and "
            + "(:state is null or r.provisioningState = :state) and "
            + "(:q is null or lower(r.name) like lower(concat('%', :q, '%')))")
    Page<AzureResourceEntity> search(String type, String rg, String state, String q, Pageable pageable);
}
