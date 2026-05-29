package com.emp_management.feature.permission.entity;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.RequestStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "permission_application")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ───────────────── EMPLOYEE ─────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // ───────────────── PERMISSION DETAILS ─────────────────

    @Column(name = "permission_date", nullable = false)
    private LocalDate permissionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, length = 500)
    private String reason;

    // ───────────────── ATTACHMENT DETAILS ─────────────────

    @Column(name = "attachment_file_name")
    private String attachmentFileName;

    @Column(name = "attachment_original_name")
    private String attachmentOriginalName;

    @Column(name = "attachment_content_type")
    private String attachmentContentType;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    // ───────────────── STATUS ─────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    // ───────────────── APPROVAL FLOW ─────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "current_approval_level")
    private ApprovalLevel currentApprovalLevel;

    @Column(name = "required_approval_levels")
    private Integer requiredApprovalLevels;

    @Column(name = "current_approver_id")
    private String currentApproverId;

    // ───────── FIRST APPROVER ─────────

    @Column(name = "first_approver_id")
    private String firstApproverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_approver_decision")
    private RequestStatus firstApproverDecision;

    @Column(name = "first_approver_decided_at")
    private LocalDateTime firstApproverDecidedAt;

    // ───────── SECOND APPROVER ─────────

    @Column(name = "second_approver_id")
    private String secondApproverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "second_approver_decision")
    private RequestStatus secondApproverDecision;

    @Column(name = "second_approver_decided_at")
    private LocalDateTime secondApproverDecidedAt;

    // ───────────────── FINAL APPROVAL ─────────────────

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_role")
    private String approvedRole;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // ───────────────── AUDIT ─────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    // ───────────────── AUTO TIMESTAMP ─────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ───────────────── TRANSIENT METHODS ─────────────────

    @Transient
    public String getEmployeeId() {
        return employee != null ? employee.getEmpId() : null;
    }

    @Transient
    public String getEmployeeName() {
        return employee != null ? employee.getName() : null;
    }

    // ───────────────── GETTERS & SETTERS ─────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getPermissionDate() {
        return permissionDate;
    }

    public void setPermissionDate(LocalDate permissionDate) {
        this.permissionDate = permissionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // ───────── ATTACHMENT ─────────

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public void setAttachmentFileName(String attachmentFileName) {
        this.attachmentFileName = attachmentFileName;
    }

    public String getAttachmentOriginalName() {
        return attachmentOriginalName;
    }

    public void setAttachmentOriginalName(String attachmentOriginalName) {
        this.attachmentOriginalName = attachmentOriginalName;
    }

    public String getAttachmentContentType() {
        return attachmentContentType;
    }

    public void setAttachmentContentType(String attachmentContentType) {
        this.attachmentContentType = attachmentContentType;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(Long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    // ───────── STATUS ─────────

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    // ───────── APPROVAL FLOW ─────────

    public ApprovalLevel getCurrentApprovalLevel() {
        return currentApprovalLevel;
    }

    public void setCurrentApprovalLevel(ApprovalLevel currentApprovalLevel) {
        this.currentApprovalLevel = currentApprovalLevel;
    }

    public Integer getRequiredApprovalLevels() {
        return requiredApprovalLevels;
    }

    public void setRequiredApprovalLevels(Integer requiredApprovalLevels) {
        this.requiredApprovalLevels = requiredApprovalLevels;
    }

    public String getCurrentApproverId() {
        return currentApproverId;
    }

    public void setCurrentApproverId(String currentApproverId) {
        this.currentApproverId = currentApproverId;
    }

    public String getFirstApproverId() {
        return firstApproverId;
    }

    public void setFirstApproverId(String firstApproverId) {
        this.firstApproverId = firstApproverId;
    }

    public RequestStatus getFirstApproverDecision() {
        return firstApproverDecision;
    }

    public void setFirstApproverDecision(RequestStatus firstApproverDecision) {
        this.firstApproverDecision = firstApproverDecision;
    }

    public LocalDateTime getFirstApproverDecidedAt() {
        return firstApproverDecidedAt;
    }

    public void setFirstApproverDecidedAt(LocalDateTime firstApproverDecidedAt) {
        this.firstApproverDecidedAt = firstApproverDecidedAt;
    }

    public String getSecondApproverId() {
        return secondApproverId;
    }

    public void setSecondApproverId(String secondApproverId) {
        this.secondApproverId = secondApproverId;
    }

    public RequestStatus getSecondApproverDecision() {
        return secondApproverDecision;
    }

    public void setSecondApproverDecision(RequestStatus secondApproverDecision) {
        this.secondApproverDecision = secondApproverDecision;
    }

    public LocalDateTime getSecondApproverDecidedAt() {
        return secondApproverDecidedAt;
    }

    public void setSecondApproverDecidedAt(LocalDateTime secondApproverDecidedAt) {
        this.secondApproverDecidedAt = secondApproverDecidedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedRole() {
        return approvedRole;
    }

    public void setApprovedRole(String approvedRole) {
        this.approvedRole = approvedRole;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}