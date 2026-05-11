package com.pfe.back.controller;

import com.pfe.back.entity.PipelineRun;
import com.pfe.back.repository.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRunRepository repo;

    @GetMapping
    public List<PipelineRun> recent() {
        return repo.findTop50ByOrderByStartedAtDesc();
    }
}
