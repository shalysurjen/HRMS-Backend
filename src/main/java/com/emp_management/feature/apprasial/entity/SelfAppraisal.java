package com.emp_management.feature.apprasial.entity;

import com.emp_management.feature.apprasial.enums.AppraisalStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "self_appraisal",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "cycle_id"}))
public class SelfAppraisal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private AppraisalCycle cycle;

    @Enumerated(EnumType.STRING)
    private AppraisalStatus status;

    private String firstApproverId;
    private String finalApproverId;

    private LocalDateTime submittedAt;

    /** NEW: timestamp when L1 reviewer completes their review (approve or reject). */
    private LocalDateTime l1ReviewedAt;

    private LocalDateTime publishedAt;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SelfAppraisal() {}

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); if (status == null) status = AppraisalStatus.DRAFT; }
    @PreUpdate  void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public AppraisalCycle getCycle() { return cycle; }
    public void setCycle(AppraisalCycle cycle) { this.cycle = cycle; }

    public AppraisalStatus getStatus() { return status; }
    public void setStatus(AppraisalStatus status) { this.status = status; }

    public String getFirstApproverId() { return firstApproverId; }
    public void setFirstApproverId(String id) { this.firstApproverId = id; }

    public String getFinalApproverId() { return finalApproverId; }
    public void setFinalApproverId(String id) { this.finalApproverId = id; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime t) { this.submittedAt = t; }

    public LocalDateTime getL1ReviewedAt() { return l1ReviewedAt; }
    public void setL1ReviewedAt(LocalDateTime t) { this.l1ReviewedAt = t; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime t) { this.publishedAt = t; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
