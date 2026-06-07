package com.emp_management.feature.apprasial.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appraisal_projects")
public class AppraisalProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private SelfAppraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private AppraisalQuestion question;

    @Column(nullable = false)
    private String projectName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist  void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SelfAppraisal getAppraisal() { return appraisal; }
    public void setAppraisal(SelfAppraisal appraisal) { this.appraisal = appraisal; }

    public AppraisalQuestion getQuestion() { return question; }
    public void setQuestion(AppraisalQuestion question) { this.question = question; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}