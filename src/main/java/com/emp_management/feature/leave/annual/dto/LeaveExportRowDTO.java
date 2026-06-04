package com.emp_management.feature.leave.annual.dto;

/**
 * One row in the Leave Export Excel — matches the image exactly:
 * Application Created Date | Employee ID | Employee Name | Leave Type |
 * Start Date | End Date | Start of the Day | No. of Days | Leave Year |
 * First Approver | First Approval Date | First Approval Decision |
 * Second Approver | Second Approval Date | Second Approval Decision
 */
public class LeaveExportRowDTO {

    private String applicationCreatedDate;  // createdAt formatted
    private String employeeId;
    private String employeeName;
    private String leaveType;
    private String startDate;
    private String endDate;
    private String startOfTheDay;           // startDateHalfDayType (FIRST_HALF / SECOND_HALF / NULL)
    private String noOfDays;
    private String leaveYear;
    private String firstApprover;           // firstApproverId
    private String firstApprovalDate;       // firstApproverDecidedAt
    private String firstApprovalDecision;   // firstApproverDecision
    private String secondApprover;          // secondApproverId
    private String secondApprovalDate;      // secondApproverDecidedAt
    private String secondApprovalDecision;  // secondApproverDecision

    public String getApplicationCreatedDate() { return applicationCreatedDate; }
    public void setApplicationCreatedDate(String v) { this.applicationCreatedDate = v; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String v) { this.employeeId = v; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String v) { this.employeeName = v; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String v) { this.leaveType = v; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String v) { this.startDate = v; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String v) { this.endDate = v; }

    public String getStartOfTheDay() { return startOfTheDay; }
    public void setStartOfTheDay(String v) { this.startOfTheDay = v; }

    public String getNoOfDays() { return noOfDays; }
    public void setNoOfDays(String v) { this.noOfDays = v; }

    public String getLeaveYear() { return leaveYear; }
    public void setLeaveYear(String v) { this.leaveYear = v; }

    public String getFirstApprover() { return firstApprover; }
    public void setFirstApprover(String v) { this.firstApprover = v; }

    public String getFirstApprovalDate() { return firstApprovalDate; }
    public void setFirstApprovalDate(String v) { this.firstApprovalDate = v; }

    public String getFirstApprovalDecision() { return firstApprovalDecision; }
    public void setFirstApprovalDecision(String v) { this.firstApprovalDecision = v; }

    public String getSecondApprover() { return secondApprover; }
    public void setSecondApprover(String v) { this.secondApprover = v; }

    public String getSecondApprovalDate() { return secondApprovalDate; }
    public void setSecondApprovalDate(String v) { this.secondApprovalDate = v; }

    public String getSecondApprovalDecision() { return secondApprovalDecision; }
    public void setSecondApprovalDecision(String v) { this.secondApprovalDecision = v; }
}
