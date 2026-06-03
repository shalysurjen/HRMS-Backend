package com.emp_management.feature.apprasial.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appraisal_remarks")
public class AppraisalRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private SelfAppraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private AppraisalQuestion question;

    private String approverId;

    @Enumerated(EnumType.STRING)
    private ApproverLevel approverLevel;

    @Column(columnDefinition = "TEXT")
    private String remarkText;

    private Integer revisedRating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ApproverLevel { L1, L2 }

    public AppraisalRemark() {}

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SelfAppraisal getAppraisal() { return appraisal; }
    public void setAppraisal(SelfAppraisal appraisal) { this.appraisal = appraisal; }
    public AppraisalQuestion getQuestion() { return question; }
    public void setQuestion(AppraisalQuestion question) { this.question = question; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public ApproverLevel getApproverLevel() { return approverLevel; }
    public void setApproverLevel(ApproverLevel approverLevel) { this.approverLevel = approverLevel; }
    public String getRemarkText() { return remarkText; }
    public void setRemarkText(String remarkText) { this.remarkText = remarkText; }
    public Integer getRevisedRating() { return revisedRating; }
    public void setRevisedRating(Integer revisedRating) { this.revisedRating = revisedRating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
