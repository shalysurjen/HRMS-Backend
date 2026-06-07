package com.emp_management.feature.attendance.dto;

public class AttendanceMonthlySummaryDTO {

    private String employeeId;
    private String employeeName;
    private int totalWorkingDays;
    private int presentDays;
    private int absentDays;
    private int halfDays;
    private int wfhDays;
    private int lopDays;
    private int leaveDays;
    private int weekendCount;
    private int holidayCount;
    private String avgWorkingHours;   // "07:30" format
    private String totalWorkingHours; // "165:20" format
    private String earliestCheckIn;   // "08:45" first punch in across month
    private String latestCheckOut;    // "19:30" last punch out across month

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public int getTotalWorkingDays() { return totalWorkingDays; }
    public void setTotalWorkingDays(int totalWorkingDays) { this.totalWorkingDays = totalWorkingDays; }

    public int getPresentDays() { return presentDays; }
    public void setPresentDays(int presentDays) { this.presentDays = presentDays; }

    public int getAbsentDays() { return absentDays; }
    public void setAbsentDays(int absentDays) { this.absentDays = absentDays; }

    public int getHalfDays() { return halfDays; }
    public void setHalfDays(int halfDays) { this.halfDays = halfDays; }

    public int getWfhDays() { return wfhDays; }
    public void setWfhDays(int wfhDays) { this.wfhDays = wfhDays; }

    public int getLopDays() { return lopDays; }
    public void setLopDays(int lopDays) { this.lopDays = lopDays; }

    public int getLeaveDays() { return leaveDays; }
    public void setLeaveDays(int leaveDays) { this.leaveDays = leaveDays; }

    public int getWeekendCount() { return weekendCount; }
    public void setWeekendCount(int weekendCount) { this.weekendCount = weekendCount; }

    public int getHolidayCount() { return holidayCount; }
    public void setHolidayCount(int holidayCount) { this.holidayCount = holidayCount; }

    public String getAvgWorkingHours() { return avgWorkingHours; }
    public void setAvgWorkingHours(String avgWorkingHours) { this.avgWorkingHours = avgWorkingHours; }

    public String getTotalWorkingHours() { return totalWorkingHours; }
    public void setTotalWorkingHours(String totalWorkingHours) { this.totalWorkingHours = totalWorkingHours; }

    public String getEarliestCheckIn() { return earliestCheckIn; }
    public void setEarliestCheckIn(String earliestCheckIn) { this.earliestCheckIn = earliestCheckIn; }

    public String getLatestCheckOut() { return latestCheckOut; }
    public void setLatestCheckOut(String latestCheckOut) { this.latestCheckOut = latestCheckOut; }
}
