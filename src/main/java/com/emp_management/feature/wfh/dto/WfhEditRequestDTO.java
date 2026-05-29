package com.emp_management.feature.wfh.dto;

import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

public class WfhEditRequestDTO {
    private String employeeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String startDateHalfDayType;
    private String endDateHalfDayType;
    private String reason;
    private MultipartFile attachment;

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStartDateHalfDayType() { return startDateHalfDayType; }
    public void setStartDateHalfDayType(String v) { this.startDateHalfDayType = v; }

    public String getEndDateHalfDayType() { return endDateHalfDayType; }
    public void setEndDateHalfDayType(String v) { this.endDateHalfDayType = v; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public MultipartFile getAttachment() { return attachment; }
    public void setAttachment(MultipartFile attachment) { this.attachment = attachment; }
}
