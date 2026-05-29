package com.emp_management.feature.permission.dto;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;

public class PermissionRequestDTO {

    private String employeeId;

    // ── FIX: @DateTimeFormat required for @ModelAttribute multipart binding ──
    // Without this, LocalDate/LocalTime fields silently become null
    // when Spring parses multipart/form-data string values.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)          // expects "2026-05-28"
    private LocalDate permissionDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)          // expects "09:30"
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)          // expects "11:00"
    private LocalTime endTime;

    private String reason;

    // ── File attachment (unchanged) ────────────────────────────────
    private MultipartFile attachment;

    // ── Getters / Setters ─────────────────────────────────────────
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getPermissionDate() { return permissionDate; }
    public void setPermissionDate(LocalDate permissionDate) { this.permissionDate = permissionDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public MultipartFile getAttachment() { return attachment; }
    public void setAttachment(MultipartFile attachment) { this.attachment = attachment; }
}