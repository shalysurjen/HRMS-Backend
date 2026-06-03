package com.emp_management.feature.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class AttendanceDetailDTO {

    private String employeeId;
    private String employeeName;
    private LocalDate date;
    private String status;
    private LocalTime checkIn;
    private LocalTime checkOut;

    // Matches DB column type tracking working hours
    private LocalTime workingHours;

    // Added punch records
    private String punchRecords;

    private boolean lopTriggered;

    // ── ✅ Excel 15 Columns Leave Applications Report-kaga Namma Pudhusa Add Panna Target Fields ──
    private String applicationCreatedDate;
    private String leaveType;
    private String startDate;
    private String endDate;
    private String startOfDay;             // Full Day / Half Day tracking
    private Double noOfDays;               // Calculated total days count
    private String leaveYear;
    private String firstApprover;
    private String firstApprovalDate;
    private String firstApprovalDecision;
    private String secondApprover;
    private String secondApprovalDate;
    private String secondApprovalDecision;

    // ── Standard Getters and Setters ──

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalTime checkIn) { this.checkIn = checkIn; }

    public LocalTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalTime checkOut) { this.checkOut = checkOut; }

    public LocalTime getWorkingHours() { return workingHours; }
    public void setWorkingHours(LocalTime workingHours) { this.workingHours = workingHours; }

    public String getPunchRecords() { return punchRecords; }
    public void setPunchRecords(String punchRecords) { this.punchRecords = punchRecords; }

    public boolean isLopTriggered() { return lopTriggered; }
    public void setLopTriggered(boolean lopTriggered) { this.lopTriggered = lopTriggered; }

    // ── ✅ New Getters & Setters for the 15 Columns dynamic structure report ──

    public String getApplicationCreatedDate() { return applicationCreatedDate; }
    public void setApplicationCreatedDate(String applicationCreatedDate) { this.applicationCreatedDate = applicationCreatedDate; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getStartOfDay() { return startOfDay; }
    public void setStartOfDay(String startOfDay) { this.startOfDay = startOfDay; }

    public Double getNoOfDays() { return noOfDays; }
    public void setNoOfDays(Double noOfDays) { this.noOfDays = noOfDays; }

    public String getLeaveYear() { return leaveYear; }
    public void setLeaveYear(String leaveYear) { this.leaveYear = leaveYear; }

    public String getFirstApprover() { return firstApprover; }
    public void setFirstApprover(String firstApprover) { this.firstApprover = firstApprover; }

    public String getFirstApprovalDate() { return firstApprovalDate; }
    public void setFirstApprovalDate(String firstApprovalDate) { this.firstApprovalDate = firstApprovalDate; }

    public String getFirstApprovalDecision() { return firstApprovalDecision; }
    public void setFirstApprovalDecision(String firstApprovalDecision) { this.firstApprovalDecision = firstApprovalDecision; }

    public String getSecondApprover() { return secondApprover; }
    public void setSecondApprover(String secondApprover) { this.secondApprover = secondApprover; }

    public String getSecondApprovalDate() { return secondApprovalDate; }
    public void setSecondApprovalDate(String secondApprovalDate) { this.secondApprovalDate = secondApprovalDate; }

    public String getSecondApprovalDecision() { return secondApprovalDecision; }
    public void setSecondApprovalDecision(String secondApprovalDecision) { this.secondApprovalDecision = secondApprovalDecision; }

    @Override
    public String toString() {
        return "AttendanceDetailDTO{" +
                "employeeId='" + employeeId + '\'' +
                ", employeeName='" + employeeName + '\'' +
                ", leaveType='" + leaveType + '\'' +
                ", date=" + date +
                '}';
    }
}