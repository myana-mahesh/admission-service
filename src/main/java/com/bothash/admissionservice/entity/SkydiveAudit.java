package com.bothash.admissionservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skydive_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkydiveAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @Column(name = "triggered_by", length = 120, nullable = false)
    private String triggeredBy;

    @Column(name = "triggered_role", length = 40)
    private String triggeredRole;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "action_name", length = 160, nullable = false)
    private String actionName;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Lob
    @Column(name = "details_json", columnDefinition = "LONGTEXT")
    private String detailsJson;
}
