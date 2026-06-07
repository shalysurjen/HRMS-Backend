package com.emp_management.feature.leave.annual.service;

import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.leave.annual.entity.LeaveApplication;
import com.emp_management.feature.leave.annual.repository.LeaveApplicationRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LeaveExportService {

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final EmployeeRepository employeeRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final String[] HEADERS = {
            "Application Created Date", "Employee ID", "Employee Name",
            "Leave Type", "Start Date", "End Date", "Start of the Day",
            "No. of Days", "Leave Year",
            "First Approver", "First Approval Date", "First Approval Decision",
            "Second Approver", "Second Approval Date", "Second Approval Decision"
    };

    public LeaveExportService(LeaveApplicationRepository leaveApplicationRepository,
                              EmployeeRepository employeeRepository) {
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.employeeRepository         = employeeRepository;
    }

    // ── Export all leaves ─────────────────────────────────────────
    public ByteArrayInputStream exportAll() {
        List<LeaveApplication> leaves = leaveApplicationRepository.findAll();
        return buildExcel(leaves);
    }

    // ── Export by month + year (uses startDate month/year) ────────
    // FIX: use dedicated JPQL query instead of findAll() + in-memory filter.
    //      Requires LeaveApplicationRepository.findByStartDateMonthAndYear() — see repo fix.
    public ByteArrayInputStream exportByMonth(int month, int year) {
        List<LeaveApplication> leaves =
                leaveApplicationRepository.findByStartDateMonthAndYear(month, year);
        return buildExcel(leaves);
    }

    // ── Build Excel ───────────────────────────────────────────────
    private ByteArrayInputStream buildExcel(List<LeaveApplication> leaves) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Leave Export");

            // ── Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows
            int rowNum = 1;
            for (LeaveApplication l : leaves) {
                Row row = sheet.createRow(rowNum++);

                // Application Created Date
                row.createCell(0).setCellValue(
                        l.getCreatedAt() != null ? l.getCreatedAt().format(DT_FMT) : "");

                // Employee ID
                row.createCell(1).setCellValue(
                        l.getEmployee() != null ? l.getEmployee().getEmpId() : "");

                // Employee Name
                row.createCell(2).setCellValue(
                        l.getEmployee() != null ? l.getEmployee().getName() : "");

                // Leave Type
                row.createCell(3).setCellValue(
                        l.getLeaveType() != null ? l.getLeaveType().getLeaveType() : "");

                // Start Date
                row.createCell(4).setCellValue(
                        l.getStartDate() != null ? l.getStartDate().format(DATE_FMT) : "");

                // End Date
                row.createCell(5).setCellValue(
                        l.getEndDate() != null ? l.getEndDate().format(DATE_FMT) : "");

                // Start of the Day (half-day type)
                String startOfDay = "";
                if (l.getStartDateHalfDayType() != null)
                    startOfDay = l.getStartDateHalfDayType().name();
                else if (l.getHalfDayType() != null)
                    startOfDay = l.getHalfDayType().name();
                row.createCell(6).setCellValue(startOfDay);

                // No. of Days
                row.createCell(7).setCellValue(
                        l.getDays() != null ? l.getDays().doubleValue() : 0);

                // Leave Year
                row.createCell(8).setCellValue(
                        l.getYear() != null ? l.getYear() : 0);

                // FIX: First Approver — resolve name from EmployeeRepository, not raw EmpId
                row.createCell(9).setCellValue(resolveApproverName(l.getFirstApproverId()));

                // First Approval Date
                row.createCell(10).setCellValue(
                        l.getFirstApproverDecidedAt() != null
                                ? l.getFirstApproverDecidedAt().format(DT_FMT) : "");

                // First Approval Decision
                row.createCell(11).setCellValue(
                        l.getFirstApproverDecision() != null
                                ? l.getFirstApproverDecision().name() : "PENDING");

                // FIX: Second Approver — resolve name from EmployeeRepository, not raw EmpId
                row.createCell(12).setCellValue(resolveApproverName(l.getSecondApproverId()));

                // Second Approval Date
                row.createCell(13).setCellValue(
                        l.getSecondApproverDecidedAt() != null
                                ? l.getSecondApproverDecidedAt().format(DT_FMT) : "");

                // Second Approval Decision
                // FIX: show "N/A" when no second approver is configured (single-level approval)
                row.createCell(14).setCellValue(
                        l.getSecondApproverDecision() != null
                                ? l.getSecondApproverDecision().name()
                                : (l.getSecondApproverId() != null ? "PENDING" : "N/A"));
            }

            // Auto-size columns
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate leave export Excel", e);
        }
    }

    /** Resolve EmpId → Employee full name. Returns "" when empId is null/blank. */
    private String resolveApproverName(String empId) {
        if (empId == null || empId.isBlank()) return "";
        return employeeRepository.findByEmpId(empId)
                .map(emp -> emp.getName())
                .orElse(empId); // fallback to empId if employee row is somehow missing
    }
}