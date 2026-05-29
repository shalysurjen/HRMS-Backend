package com.emp_management.feature.permission.dto;

import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class PermissionResponseDTO {

    private Long id;
    private String employeeId;
    private String employeeName;
    private LocalDate permissionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String durationFormatted;
    private String reason;
    private RequestStatus status;
    private String rejectionReason;

    // ── Approval chain (unchanged) ─────────────────────────────────
    private Integer requiredApprovalLevels;
    private ApprovalLevel currentApprovalLevel;
    private String currentApproverId;

    private String firstApproverId;
    private RequestStatus firstApproverDecision;
    private LocalDateTime firstApproverDecidedAt;

    private String secondApproverId;
    private RequestStatus secondApproverDecision;
    private LocalDateTime secondApproverDecidedAt;

    private String approvedBy;
    private String approvedRole;
    private LocalDateTime approvedAt;

    // ── Audit (unchanged) ──────────────────────────────────────────
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ── NEW: attachment fields (maps to your 5 new MySQL columns) ──
    private String attachmentFileName;       // stored UUID-based filename
    private String attachmentOriginalName;   // original name shown to user
    private String attachmentContentType;    // image/jpeg, application/pdf etc.
    private String attachmentPath;           // full path on disk
    private Long   attachmentSize;           // bytes

    // ── Existing getters/setters (unchanged) ──────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public LocalDate getPermissionDate() { return permissionDate; }
    public void setPermissionDate(LocalDate permissionDate) { this.permissionDate = permissionDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getDurationFormatted() { return durationFormatted; }
    public void setDurationFormatted(String durationFormatted) { this.durationFormatted = durationFormatted; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Integer getRequiredApprovalLevels() { return requiredApprovalLevels; }
    public void setRequiredApprovalLevels(Integer requiredApprovalLevels) { this.requiredApprovalLevels = requiredApprovalLevels; }

    public ApprovalLevel getCurrentApprovalLevel() { return currentApprovalLevel; }
    public void setCurrentApprovalLevel(ApprovalLevel currentApprovalLevel) { this.currentApprovalLevel = currentApprovalLevel; }

    public String getCurrentApproverId() { return currentApproverId; }
    public void setCurrentApproverId(String currentApproverId) { this.currentApproverId = currentApproverId; }

    public String getFirstApproverId() { return firstApproverId; }
    public void setFirstApproverId(String firstApproverId) { this.firstApproverId = firstApproverId; }

    public RequestStatus getFirstApproverDecision() { return firstApproverDecision; }
    public void setFirstApproverDecision(RequestStatus firstApproverDecision) { this.firstApproverDecision = firstApproverDecision; }

    public LocalDateTime getFirstApproverDecidedAt() { return firstApproverDecidedAt; }
    public void setFirstApproverDecidedAt(LocalDateTime firstApproverDecidedAt) { this.firstApproverDecidedAt = firstApproverDecidedAt; }

    public String getSecondApproverId() { return secondApproverId; }
    public void setSecondApproverId(String secondApproverId) { this.secondApproverId = secondApproverId; }

    public RequestStatus getSecondApproverDecision() { return secondApproverDecision; }
    public void setSecondApproverDecision(RequestStatus secondApproverDecision) { this.secondApproverDecision = secondApproverDecision; }

    public LocalDateTime getSecondApproverDecidedAt() { return secondApproverDecidedAt; }
    public void setSecondApproverDecidedAt(LocalDateTime secondApproverDecidedAt) { this.secondApproverDecidedAt = secondApproverDecidedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovedRole() { return approvedRole; }
    public void setApprovedRole(String approvedRole) { this.approvedRole = approvedRole; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    // ── NEW getters/setters ────────────────────────────────────────

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public String getAttachmentOriginalName() { return attachmentOriginalName; }
    public void setAttachmentOriginalName(String attachmentOriginalName) { this.attachmentOriginalName = attachmentOriginalName; }

    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String attachmentContentType) { this.attachmentContentType = attachmentContentType; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public Long getAttachmentSize() { return attachmentSize; }
    public void setAttachmentSize(Long attachmentSize) { this.attachmentSize = attachmentSize; }
}