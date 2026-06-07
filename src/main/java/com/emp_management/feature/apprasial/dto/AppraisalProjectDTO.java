package com.emp_management.feature.apprasial.dto;

import java.time.LocalDateTime;

public class AppraisalProjectDTO {

    // ── Request DTO (add / update) ───────────────────────────────────────────
    public static class Request {
        private Long questionId;
        private String projectName;
        private String description;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ── Response DTO ─────────────────────────────────────────────────────────
    public static class Response {
        private Long id;
        private Long appraisalId;
        private Long questionId;
        private String projectName;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAppraisalId() { return appraisalId; }
        public void setAppraisalId(Long appraisalId) { this.appraisalId = appraisalId; }
        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}