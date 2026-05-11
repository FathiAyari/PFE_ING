package com.pfe.back.repository;

import com.pfe.back.entity.DockerImage;
import com.pfe.back.entity.ImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DockerImageRepository extends JpaRepository<DockerImage, Long> {
    List<DockerImage> findByStatusOrderByScannedAtDesc(ImageStatus status);
    long countByStatus(ImageStatus status);
}
