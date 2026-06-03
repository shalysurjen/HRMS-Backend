package com.emp_management.feature.apprasial.dto;

import com.emp_management.feature.apprasial.entity.AppraisalStatusHistory;
import com.emp_management.feature.apprasial.enums.AppraisalStatus;

import java.time.LocalDateTime;
import java.util.List;

public class AppraisalDetailDTO {

    private Long appraisalId;
    private String employeeId;
    private String employeeName;
    private String role;
    private String department;
    private String doj;
    private String totalExperience;
    private String companyExperience;
    private String experienceType;
    private String reportingManager;
    private String cycleLabel;
    private AppraisalStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime l1ReviewedAt;   // NEW
    private LocalDateTime publishedAt;

    /** NEW: overall average of all self-ratings across all sections. */
    private Double overallAvgRating;

    // Approver identity fields — always populated so L2 can see L1's info
    private String firstApproverId;
    private String firstApproverName;
    private String finalApproverId;
    private String finalApproverName;
    private String l1OverallRemark;
    private String l2OverallRemark;

    private List<SectionDTO> sections;
    private List<StatusHistoryDTO> statusHistory;

    public AppraisalDetailDTO() {}

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getAppraisalId() { return appraisalId; }          public void setAppraisalId(Long v)          { appraisalId = v; }
    public String getEmployeeId() { return employeeId; }          public void setEmployeeId(String v)         { employeeId = v; }
    public String getEmployeeName() { return employeeName; }      public void setEmployeeName(String v)       { employeeName = v; }
    public String getRole() { return role; }                      public void setRole(String v)               { role = v; }
    public String getDepartment() { return department; }          public void setDepartment(String v)         { department = v; }
    public String getDoj() { return doj; }                        public void setDoj(String v)                { doj = v; }
    public String getTotalExperience() { return totalExperience; } public void setTotalExperience(String v)   { totalExperience = v; }
    public String getCompanyExperience() { return companyExperience; } public void setCompanyExperience(String v) { companyExperience = v; }
    public String getExperienceType() { return experienceType; }  public void setExperienceType(String v)     { experienceType = v; }
    public String getReportingManager() { return reportingManager; } public void setReportingManager(String v) { reportingManager = v; }
    public String getCycleLabel() { return cycleLabel; }          public void setCycleLabel(String v)         { cycleLabel = v; }
    public AppraisalStatus getStatus() { return status; }         public void setStatus(AppraisalStatus v)    { status = v; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime v) { submittedAt = v; }
    public LocalDateTime getL1ReviewedAt() { return l1ReviewedAt; } public void setL1ReviewedAt(LocalDateTime v) { l1ReviewedAt = v; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(LocalDateTime v) { publishedAt = v; }
    public Double getOverallAvgRating() { return overallAvgRating; } public void setOverallAvgRating(Double v) { overallAvgRating = v; }
    public String getFirstApproverId() { return firstApproverId; }   public void setFirstApproverId(String v)   { firstApproverId = v; }
    public String getFirstApproverName() { return firstApproverName; } public void setFirstApproverName(String v) { firstApproverName = v; }
    public String getFinalApproverId() { return finalApproverId; }   public void setFinalApproverId(String v)   { finalApproverId = v; }
    public String getFinalApproverName() { return finalApproverName; } public void setFinalApproverName(String v) { finalApproverName = v; }
    public String getL1OverallRemark() { return l1OverallRemark; }   public void setL1OverallRemark(String v)   { l1OverallRemark = v; }
    public String getL2OverallRemark() { return l2OverallRemark; }   public void setL2OverallRemark(String v)   { l2OverallRemark = v; }
    public List<SectionDTO> getSections() { return sections; }    public void setSections(List<SectionDTO> v) { sections = v; }
    public List<StatusHistoryDTO> getStatusHistory() { return statusHistory; } public void setStatusHistory(List<StatusHistoryDTO> v) { statusHistory = v; }

    // ─────────────────────────────────────────────────────────────────────────

    public static class SectionDTO {
        private String sectionName;
        private List<QuestionAnswerDTO> questions;

        /** NEW: average of selfRating for all rated questions in this section. */
        private Double sectionAvgRating;

        public SectionDTO() {}
        public String getSectionName() { return sectionName; }          public void setSectionName(String v)      { sectionName = v; }
        public List<QuestionAnswerDTO> getQuestions() { return questions; } public void setQuestions(List<QuestionAnswerDTO> v) { questions = v; }
        public Double getSectionAvgRating() { return sectionAvgRating; } public void setSectionAvgRating(Double v) { sectionAvgRating = v; }
    }

    public static class QuestionAnswerDTO {
        private Long questionId;
        private String questionText;
        private String inputType;
        private boolean isRequired;

        // Employee self-assessment
        private String answerText;
        private Integer selfRating;

        // L1 reviewer fields (stored on SelfAppraisalAnswer)
        private Integer revisedRating;
        private String revisedRemarks;

        // L2 / final reviewer fields (stored on SelfAppraisalAnswer)
        private Integer finalRating;
        private String finalRemarks;

        // Legacy remark fields sourced from AppraisalRemark (kept for backward compat)
        private String l1Remark;
        private Integer l1RevisedRating;
        private String l2Remark;
        private Integer l2RevisedRating;

        public QuestionAnswerDTO() {}

        public Long getQuestionId() { return questionId; }               public void setQuestionId(Long v)          { questionId = v; }
        public String getQuestionText() { return questionText; }         public void setQuestionText(String v)      { questionText = v; }
        public String getInputType() { return inputType; }               public void setInputType(String v)         { inputType = v; }
        public boolean isRequired() { return isRequired; }               public void setRequired(boolean v)         { isRequired = v; }
        public String getAnswerText() { return answerText; }             public void setAnswerText(String v)        { answerText = v; }
        public Integer getSelfRating() { return selfRating; }            public void setSelfRating(Integer v)       { selfRating = v; }
        public Integer getRevisedRating() { return revisedRating; }      public void setRevisedRating(Integer v)    { revisedRating = v; }
        public String getRevisedRemarks() { return revisedRemarks; }     public void setRevisedRemarks(String v)    { revisedRemarks = v; }
        public Integer getFinalRating() { return finalRating; }          public void setFinalRating(Integer v)      { finalRating = v; }
        public String getFinalRemarks() { return finalRemarks; }         public void setFinalRemarks(String v)      { finalRemarks = v; }
        public String getL1Remark() { return l1Remark; }                 public void setL1Remark(String v)          { l1Remark = v; }
        public Integer getL1RevisedRating() { return l1RevisedRating; }  public void setL1RevisedRating(Integer v)  { l1RevisedRating = v; }
        public String getL2Remark() { return l2Remark; }                 public void setL2Remark(String v)          { l2Remark = v; }
        public Integer getL2RevisedRating() { return l2RevisedRating; }  public void setL2RevisedRating(Integer v)  { l2RevisedRating = v; }
    }

    public static class StatusHistoryDTO {
        private String fromStatus;
        private String toStatus;
        private String changedBy;

        /** NEW: human-readable name of the actor (e.g. "Ravi Kumar"). */
        private String changedByName;

        /** NEW: semantic action type for timeline badges / filtering. */
        private String actionType;

        private String remarks;
        private LocalDateTime changedAt;

        public StatusHistoryDTO() {}

        public String getFromStatus() { return fromStatus; }         public void setFromStatus(String v)       { fromStatus = v; }
        public String getToStatus() { return toStatus; }             public void setToStatus(String v)         { toStatus = v; }
        public String getChangedBy() { return changedBy; }           public void setChangedBy(String v)        { changedBy = v; }
        public String getChangedByName() { return changedByName; }   public void setChangedByName(String v)    { changedByName = v; }
        public String getActionType() { return actionType; }         public void setActionType(String v)       { actionType = v; }
        public String getRemarks() { return remarks; }               public void setRemarks(String v)          { remarks = v; }
        public LocalDateTime getChangedAt() { return changedAt; }    public void setChangedAt(LocalDateTime v) { changedAt = v; }
    }
}
