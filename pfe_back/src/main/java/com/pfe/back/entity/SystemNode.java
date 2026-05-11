package com.pfe.back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "system_nodes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;          // VM, CONTAINER, BACKEND, DATABASE

    @Column(nullable = false)
    private String status;        // UP, DOWN, DEGRADED

    private double cpuUsage;      // 0..100
    private double memoryUsage;   // 0..100
    private double diskUsage;     // 0..100

    private String host;
    private String ipAddress;

    @Column(nullable = false)
    private Instant lastCheck;
}
