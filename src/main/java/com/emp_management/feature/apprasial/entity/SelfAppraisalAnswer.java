package com.emp_management.feature.apprasial.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "self_appraisal_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"appraisal_id", "question_id"}))
public class SelfAppraisalAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private SelfAppraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private AppraisalQuestion question;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    private Integer selfRating;

    // ── NEW: L1 reviewer fields ───────────────────────────────────────────────
    /** Rating revised by L1 reviewer (firstApprover). */
    private Integer revisedRating;

    /** Textual remarks by L1 reviewer. */
    @Column(columnDefinition = "TEXT")
    private String revisedRemarks;

    // ── NEW: L2 / final reviewer fields ──────────────────────────────────────
    /** Final rating set by L2 reviewer (finalApprover). */
    private Integer finalRating;

    /** Textual remarks by L2 reviewer. */
    @Column(columnDefinition = "TEXT")
    private String finalRemarks;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SelfAppraisalAnswer() {}

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SelfAppraisal getAppraisal() { return appraisal; }
    public void setAppraisal(SelfAppraisal appraisal) { this.appraisal = appraisal; }

    public AppraisalQuestion getQuestion() { return question; }
    public void setQuestion(AppraisalQuestion question) { this.question = question; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public Integer getSelfRating() { return selfRating; }
    public void setSelfRating(Integer selfRating) { this.selfRating = selfRating; }

    public Integer getRevisedRating() { return revisedRating; }
    public void setRevisedRating(Integer revisedRating) { this.revisedRating = revisedRating; }

    public String getRevisedRemarks() { return revisedRemarks; }
    public void setRevisedRemarks(String revisedRemarks) { this.revisedRemarks = revisedRemarks; }

    public Integer getFinalRating() { return finalRating; }
    public void setFinalRating(Integer finalRating) { this.finalRating = finalRating; }

    public String getFinalRemarks() { return finalRemarks; }
    public void setFinalRemarks(String finalRemarks) { this.finalRemarks = finalRemarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
