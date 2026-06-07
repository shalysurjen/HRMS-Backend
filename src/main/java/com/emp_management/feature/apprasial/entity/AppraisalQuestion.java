package com.emp_management.feature.apprasial.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "appraisal_questions")
public class AppraisalQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private AppraisalCycle cycle;

    private String section;
    private int sortOrder;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    private InputType inputType;

    private boolean isRequired;
    private boolean isDeleted;

    public enum InputType { TEXTAREA, RATING, TEXT }

    public AppraisalQuestion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppraisalCycle getCycle() { return cycle; }
    public void setCycle(AppraisalCycle cycle) { this.cycle = cycle; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public InputType getInputType() { return inputType; }
    public void setInputType(InputType inputType) { this.inputType = inputType; }
    public boolean isRequired() { return isRequired; }
    public void setRequired(boolean required) { isRequired = required; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
