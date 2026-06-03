package com.emp_management.feature.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row per date in the attendance report.
 * Combines:
 *  - attendance_summary  → checkIn, checkOut, workingHours, status, punchRecords
 *  - leave_application   → sickLeaveDays, annualLeaveDays
 *  - wfh_application     → wfhDays
 *  - permission_application → permissionMinutes
 */
public class AttendanceReportRowDTO {

    private String    employeeId;
    private String    employeeName;
    private LocalDate date;

    // ── From attendance_summary ───────────────────────────────────
    private LocalTime checkIn;
    private LocalTime checkOut;
    private LocalTime workingHours;
    private String    status;
    private String    punchRecords;

    // ── From leave_application (APPROVED) ────────────────────────
    private Double sickLeaveDays;
    private Double annualLeaveDays;

    // ── From wfh_application (APPROVED) ──────────────────────────
    private BigDecimal wfhDays;

    // ── From permission_application (APPROVED) ───────────────────
    private Integer permissionMinutes;

    // ── Getters & Setters ─────────────────────────────────────────

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalTime checkIn) { this.checkIn = checkIn; }

    public LocalTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalTime checkOut) { this.checkOut = checkOut; }

    public LocalTime getWorkingHours() { return workingHours; }
    public void setWorkingHours(LocalTime workingHours) { this.workingHours = workingHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPunchRecords() { return punchRecords; }
    public void setPunchRecords(String punchRecords) { this.punchRecords = punchRecords; }

    public Double getSickLeaveDays() { return sickLeaveDays; }
    public void setSickLeaveDays(Double sickLeaveDays) { this.sickLeaveDays = sickLeaveDays; }

    public Double getAnnualLeaveDays() { return annualLeaveDays; }
    public void setAnnualLeaveDays(Double annualLeaveDays) { this.annualLeaveDays = annualLeaveDays; }

    public BigDecimal getWfhDays() { return wfhDays; }
    public void setWfhDays(BigDecimal wfhDays) { this.wfhDays = wfhDays; }

    public Integer getPermissionMinutes() { return permissionMinutes; }
    public void setPermissionMinutes(Integer permissionMinutes) {
        this.permissionMinutes = permissionMinutes;
    }
}
