package com.emp_management.feature.attendance.dto;

public class AttendanceDetailedRowDTO {

    // From attendance_summary
    private String empId;
    private String empName;
    private String date;
    private String checkIn;
    private String checkOut;
    private String workHours;
    private String punchRecords;

    // From carry_forward_leave_application (APPROVED, date overlaps)
    private Double cfLeaveDays;

    // From leave_application (APPROVED, date overlaps)
    // GL = General/Annual leave type
    private Double glDays;
    // SL = Sick leave type
    private Double slDays;
    // LOP = loss_of_pay_applied
    private Double lopDays;

    // From wfh_application (APPROVED, date overlaps) — 1 day per date
    private Double wfhDays;

    // From permission_application (APPROVED, on that date) — formatted as "Xh Ym"
    private String permissionHours;

    // From attendance_summary.attendance_status (PRESENT / ABSENT / WEEKEND / HOLIDAY etc.)
    private String attendanceStatus;

    // Derived application status: LEVEL 1 PENDING / LEVEL 2 PENDING / APPROVED / REJECTED etc.
    private String approvalStatus;

    // Getters & Setters
    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getCheckIn() { return checkIn; }
    public void setCheckIn(String checkIn) { this.checkIn = checkIn; }

    public String getCheckOut() { return checkOut; }
    public void setCheckOut(String checkOut) { this.checkOut = checkOut; }

    public String getWorkHours() { return workHours; }
    public void setWorkHours(String workHours) { this.workHours = workHours; }

    public String getPunchRecords() { return punchRecords; }
    public void setPunchRecords(String punchRecords) { this.punchRecords = punchRecords; }

    public Double getCfLeaveDays() { return cfLeaveDays; }
    public void setCfLeaveDays(Double cfLeaveDays) { this.cfLeaveDays = cfLeaveDays; }

    public Double getGlDays() { return glDays; }
    public void setGlDays(Double glDays) { this.glDays = glDays; }

    public Double getSlDays() { return slDays; }
    public void setSlDays(Double slDays) { this.slDays = slDays; }

    public Double getLopDays() { return lopDays; }
    public void setLopDays(Double lopDays) { this.lopDays = lopDays; }

    public Double getWfhDays() { return wfhDays; }
    public void setWfhDays(Double wfhDays) { this.wfhDays = wfhDays; }

    public String getPermissionHours() { return permissionHours; }
    public void setPermissionHours(String permissionHours) { this.permissionHours = permissionHours; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
}
