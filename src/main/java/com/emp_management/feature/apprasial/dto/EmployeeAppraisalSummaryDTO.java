package com.emp_management.feature.apprasial.dto;
import com.emp_management.feature.apprasial.enums.AppraisalStatus;
import java.time.LocalDateTime;

public class EmployeeAppraisalSummaryDTO {
    private Long appraisalId;
    private String employeeId;
    private String employeeName;
    private String cycleLabel;
    private AppraisalStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime publishedAt;

    public EmployeeAppraisalSummaryDTO() {}

    public Long getAppraisalId() { return appraisalId; } public void setAppraisalId(Long v) { appraisalId = v; }
    public String getEmployeeId() { return employeeId; } public void setEmployeeId(String v) { employeeId = v; }
    public String getEmployeeName() { return employeeName; } public void setEmployeeName(String v) { employeeName = v; }
    public String getCycleLabel() { return cycleLabel; } public void setCycleLabel(String v) { cycleLabel = v; }
    public AppraisalStatus getStatus() { return status; } public void setStatus(AppraisalStatus v) { status = v; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime v) { submittedAt = v; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(LocalDateTime v) { publishedAt = v; }
}
