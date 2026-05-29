package com.emp_management.feature.wfh.dto;

import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.HalfDayType;
import com.emp_management.shared.enums.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class WfhResponseDTO {

    private Long id;
    private String employeeId;
    private String employeeName;

    private LocalDate startDate;
    private LocalDate endDate;
    private HalfDayType startDateHalfDayType;
    private HalfDayType endDateHalfDayType;
    private BigDecimal totalDays;
    private String reason;

    private RequestStatus status;
    private String rejectionReason;

    // Approval chain
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

    // Attachment
    private String attachmentFileName;
    private String attachmentOriginalName;
    private String attachmentContentType;
    private String attachmentPath;
    private Long attachmentSize;

    // Audit
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ─── Getters & Setters ────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public HalfDayType getStartDateHalfDayType() { return startDateHalfDayType; }
    public void setStartDateHalfDayType(HalfDayType startDateHalfDayType) { this.startDateHalfDayType = startDateHalfDayType; }

    public HalfDayType getEndDateHalfDayType() { return endDateHalfDayType; }
    public void setEndDateHalfDayType(HalfDayType endDateHalfDayType) { this.endDateHalfDayType = endDateHalfDayType; }

    public BigDecimal getTotalDays() { return totalDays; }
    public void setTotalDays(BigDecimal totalDays) { this.totalDays = totalDays; }

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

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String n) { this.attachmentFileName = n; }

    public String getAttachmentOriginalName() { return attachmentOriginalName; }
    public void setAttachmentOriginalName(String n) { this.attachmentOriginalName = n; }

    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String t) { this.attachmentContentType = t; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String p) { this.attachmentPath = p; }

    public Long getAttachmentSize() { return attachmentSize; }
    public void setAttachmentSize(Long s) { this.attachmentSize = s; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
