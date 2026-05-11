package com.pfe.back.controller;

import com.pfe.back.entity.DockerImage;
import com.pfe.back.entity.ImageStatus;
import com.pfe.back.repository.DockerImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class DockerImageController {

    private final DockerImageRepository repo;

    @GetMapping
    public List<DockerImage> all() { return repo.findAll(); }

    @GetMapping("/safe")
    public List<DockerImage> safe() {
        return repo.findByStatusOrderByScannedAtDesc(ImageStatus.SAFE);
    }

    @GetMapping("/unsafe")
    public List<DockerImage> unsafe() {
        return repo.findByStatusOrderByScannedAtDesc(ImageStatus.UNSAFE);
    }

    @GetMapping("/{id}")
    public DockerImage one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow();
    }
}
