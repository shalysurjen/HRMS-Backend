package com.emp_management.feature.attendance.service;

import com.emp_management.feature.attendance.dto.AttendanceReportRowDTO;
import com.emp_management.feature.attendance.entity.AttendanceSummary;
import com.emp_management.feature.attendance.repository.AttendanceSummaryRepository;
import com.emp_management.feature.leave.annual.entity.LeaveApplication;
import com.emp_management.feature.leave.annual.repository.LeaveApplicationRepository;
import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.feature.permission.repository.PermissionRepository;
import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.feature.wfh.repository.WfhApplicationRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceReportService {

    private final AttendanceSummaryRepository attRepo;
    private final LeaveApplicationRepository  leaveRepo;
    private final WfhApplicationRepository    wfhRepo;
    private final PermissionRepository        permRepo;

    public AttendanceReportService(
            AttendanceSummaryRepository attRepo,
            LeaveApplicationRepository leaveRepo,
            WfhApplicationRepository wfhRepo,
            PermissionRepository permRepo) {
        this.attRepo   = attRepo;
        this.leaveRepo = leaveRepo;
        this.wfhRepo   = wfhRepo;
        this.permRepo  = permRepo;
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN: Get report for one employee in date range
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AttendanceReportRowDTO> getEmployeeReport(
            String empId, String empName, LocalDate fromDate, LocalDate toDate) {

        // ── 1. Attendance summary ─────────────────────────────────
        List<AttendanceSummary> attList =
                attRepo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        empId, fromDate, toDate);
        Map<LocalDate, AttendanceSummary> attMap = attList.stream()
                .collect(Collectors.toMap(
                        AttendanceSummary::getAttendanceDate,
                        a -> a, (a, b) -> a));

        // ── 2. Approved leaves — filter by empId in DB ────────────
        // Uses empId filter directly in query — no lazy loading issue
        List<LeaveApplication> leaveList =
                leaveRepo.findByEmployee_EmpIdAndStatusIn(
                        empId,
                        List.of(com.emp_management.shared.enums.RequestStatus.APPROVED),
                        fromDate, toDate);

        // ── 3. Approved WFH ───────────────────────────────────────
        List<WfhApplication> wfhList =
                wfhRepo.findApprovedByEmpIdAndDateRange(empId, fromDate, toDate);

        // ── 4. Approved Permissions ───────────────────────────────
        List<Permission> permList =
                permRepo.findApprovedByEmpIdAndDateRange(empId, fromDate, toDate);
        Map<LocalDate, Permission> permMap = permList.stream()
                .collect(Collectors.toMap(
                        Permission::getPermissionDate,
                        p -> p, (a, b) -> a));

        // ── 5. Collect all dates that have ANY data ───────────────
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(attMap.keySet());
        allDates.addAll(permMap.keySet());

        // Leave dates — expand range
        for (LeaveApplication l : leaveList) {
            if (l.getStartDate() == null || l.getEndDate() == null) continue;
            LocalDate cursor = l.getStartDate().isBefore(fromDate)
                    ? fromDate : l.getStartDate();
            LocalDate end = l.getEndDate().isAfter(toDate)
                    ? toDate : l.getEndDate();
            while (!cursor.isAfter(end)) {
                allDates.add(cursor);
                cursor = cursor.plusDays(1);
            }
        }

        // WFH dates — expand range
        for (WfhApplication w : wfhList) {
            if (w.getStartDate() == null || w.getEndDate() == null) continue;
            LocalDate cursor = w.getStartDate().isBefore(fromDate)
                    ? fromDate : w.getStartDate();
            LocalDate end = w.getEndDate().isAfter(toDate)
                    ? toDate : w.getEndDate();
            while (!cursor.isAfter(end)) {
                allDates.add(cursor);
                cursor = cursor.plusDays(1);
            }
        }

        // ── 6. Build rows — latest date first ─────────────────────
        List<LocalDate> sortedDates = new ArrayList<>(allDates);
        sortedDates.sort(Comparator.reverseOrder());

        List<AttendanceReportRowDTO> rows = new ArrayList<>();

        for (LocalDate date : sortedDates) {
            AttendanceReportRowDTO row = new AttendanceReportRowDTO();
            row.setEmployeeId(empId);
            row.setEmployeeName(empName);
            row.setDate(date);

            // attendance_summary
            AttendanceSummary att = attMap.get(date);
            if (att != null) {
                row.setCheckIn(att.getCheckIn());
                row.setCheckOut(att.getCheckOut());
                row.setWorkingHours(att.getWorkingHours());
                row.setStatus(att.getAttendanceStatus() != null
                        ? att.getAttendanceStatus().trim() : null);
                row.setPunchRecords(att.getPunchRecords());
            }

            // Leave data — sum sick + annual
            double sick   = 0.0;
            double annual = 0.0;
            for (LeaveApplication l : leaveList) {
                if (l.getStartDate() == null || l.getEndDate() == null) continue;
                if (date.isBefore(l.getStartDate()) || date.isAfter(l.getEndDate())) continue;
                double days = calculateDaysOnDate(l, date);
                String type = l.getLeaveType() != null
                        ? l.getLeaveType().getLeaveType() : "";
                if (type.equalsIgnoreCase("SICK") ||
                        type.equalsIgnoreCase("SICK_LEAVE")) {
                    sick += days;
                } else if (type.equalsIgnoreCase("ANNUAL") ||
                        type.equalsIgnoreCase("ANNUAL_LEAVE")) {
                    annual += days;
                }
            }
            if (sick   > 0) row.setSickLeaveDays(sick);
            if (annual > 0) row.setAnnualLeaveDays(annual);

            // WFH
            BigDecimal wfhDays = resolveWfhDays(date, wfhList);
            row.setWfhDays(wfhDays);

            // Permission
            Permission perm = permMap.get(date);
            if (perm != null) row.setPermissionMinutes(perm.getDurationMinutes());

            rows.add(row);
        }

        return rows;
    }

    // ═══════════════════════════════════════════════════════════════
    // 15-COLUMN EXCEL EXPORT  (leave_application real DB data)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Exports leave applications for one employee in the given date range
     * as a 15-column Excel file.  Every value comes directly from the DB —
     * no hardcoded strings anywhere.
     *
     * Columns (matching Image-4 requirement):
     *  1  Application Created Date   → leave_application.created_at
     *  2  Employee ID                → leave_application.employee.empId
     *  3  Employee Name              → leave_application.employee.name
     *  4  Leave Type                 → leave_application.leave_type.leaveType
     *  5  Start Date                 → leave_application.start_date
     *  6  End Date                   → leave_application.end_date
     *  7  Start of the Day           → start_date_half_day_type / end_date_half_day_type (NULL = full day)
     *  8  No. Of Days                → leave_application.days
     *  9  Leave Year                 → leave_application.leave_year
     * 10  First Approver             → leave_application.first_approver_id
     * 11  First Approval Date        → leave_application.first_approver_decided_at
     * 12  First Approval Decision    → leave_application.first_approver_decision
     * 13  Second Approver            → leave_application.second_approver_id
     * 14  Second Approval Date       → leave_application.second_approver_decided_at
     * 15  Second Approval Decision   → leave_application.second_approver_decision
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportToExcel15Col(
            String empId, LocalDate fromDate, LocalDate toDate) {

        // Fetch all leave applications for this employee in range
        // JOIN FETCH employee + leaveType ensures no LazyInitializationException
        // Filters by start_date so all leaves starting in the range are included
        List<LeaveApplication> leaves =
                leaveRepo.findForExportByEmpIdAndDateRange(empId, fromDate, toDate);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance Report");

            // ── Header style ──────────────────────────────────────
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            CellStyle hStyle = workbook.createCellStyle();
            hStyle.setFont(font);
            hStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hStyle.setBorderBottom(BorderStyle.THIN);

            // ── Header row ────────────────────────────────────────
            String[] cols = {
                    "Application Created Date",
                    "Employee ID",
                    "Employee Name",
                    "Leave Type",
                    "Start Date",
                    "End Date",
                    "Start of the Day",
                    "No.Of Days",
                    "Leave Year",
                    "First Approver",
                    "First Approval Date",
                    "First Approval Decision",
                    "Second Approver",
                    "Second Approval Date",
                    "Second Approval Decision"
            };
            Row hRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hStyle);
            }

            // ── Data rows — every value read from DB entity ───────
            int idx = 1;
            for (LeaveApplication l : leaves) {
                Row row = sheet.createRow(idx++);

                // 1. Application Created Date — leave_application.created_at
                row.createCell(0).setCellValue(
                        l.getCreatedAt() != null
                                ? l.getCreatedAt().toLocalDate().toString()
                                : "");

                // 2. Employee ID — employee.empId (transient helper)
                row.createCell(1).setCellValue(
                        l.getEmployeeId() != null ? l.getEmployeeId() : "");

                // 3. Employee Name — employee.name (transient helper)
                row.createCell(2).setCellValue(
                        l.getEmployeeName() != null ? l.getEmployeeName() : "");

                // 4. Leave Type — leave_type.leaveType
                row.createCell(3).setCellValue(
                        l.getLeaveType() != null && l.getLeaveType().getLeaveType() != null
                                ? l.getLeaveType().getLeaveType()
                                : "");

                // 5. Start Date — leave_application.start_date
                row.createCell(4).setCellValue(
                        l.getStartDate() != null ? l.getStartDate().toString() : "");

                // 6. End Date — leave_application.end_date
                row.createCell(5).setCellValue(
                        l.getEndDate() != null ? l.getEndDate().toString() : "");

                // 7. Start of the Day — start_date_half_day_type or end_date_half_day_type
                //    NULL means Full Day; any HalfDayType enum value means Half Day
                String startOfDay = "NULL";
                if (l.getStartDateHalfDayType() != null) {
                    startOfDay = l.getStartDateHalfDayType().name();
                } else if (l.getEndDateHalfDayType() != null) {
                    startOfDay = l.getEndDateHalfDayType().name();
                }
                row.createCell(6).setCellValue(startOfDay);

                // 8. No. Of Days — leave_application.days
                row.createCell(7).setCellValue(
                        l.getDays() != null ? l.getDays().doubleValue() : 0);

                // 9. Leave Year — leave_application.leave_year
                row.createCell(8).setCellValue(
                        l.getYear() != null ? l.getYear().toString() : "");

                // 10. First Approver — leave_application.first_approver_id
                row.createCell(9).setCellValue(
                        l.getFirstApproverId() != null ? l.getFirstApproverId() : "NULL");

                // 11. First Approval Date — leave_application.first_approver_decided_at
                row.createCell(10).setCellValue(
                        l.getFirstApproverDecidedAt() != null
                                ? l.getFirstApproverDecidedAt().toLocalDate().toString()
                                : "NULL");

                // 12. First Approval Decision — leave_application.first_approver_decision
                row.createCell(11).setCellValue(
                        l.getFirstApproverDecision() != null
                                ? l.getFirstApproverDecision().name()
                                : "NULL");

                // 13. Second Approver — leave_application.second_approver_id
                row.createCell(12).setCellValue(
                        l.getSecondApproverId() != null ? l.getSecondApproverId() : "NULL");

                // 14. Second Approval Date — leave_application.second_approver_decided_at
                row.createCell(13).setCellValue(
                        l.getSecondApproverDecidedAt() != null
                                ? l.getSecondApproverDecidedAt().toLocalDate().toString()
                                : "NULL");

                // 15. Second Approval Decision — leave_application.second_approver_decision
                row.createCell(14).setCellValue(
                        l.getSecondApproverDecision() != null
                                ? l.getSecondApproverDecision().name()
                                : "NULL");
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate 15-column Excel", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 12-COLUMN EXCEL EXPORT  (attendance_summary based — unchanged)
    // ═══════════════════════════════════════════════════════════════

    public ByteArrayInputStream exportToExcel(
            List<AttendanceReportRowDTO> rows,
            String employeeName, String employeeId) {

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance Report");

            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            CellStyle hStyle = workbook.createCellStyle();
            hStyle.setFont(font);
            hStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hStyle.setBorderBottom(BorderStyle.THIN);

            String[] cols = {
                    "Emp ID", "Emp Name", "Date", "Status",
                    "Check In", "Check Out", "Working Hours",
                    "Sick Leave (Days)", "Annual Leave (Days)",
                    "WFH (Days)", "Permission (Hrs)", "Punches"
            };
            Row hRow = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hStyle);
            }

            int idx = 1;
            for (AttendanceReportRowDTO r : rows) {
                Row row = sheet.createRow(idx++);
                row.createCell(0).setCellValue(employeeId);
                row.createCell(1).setCellValue(employeeName);
                row.createCell(2).setCellValue(r.getDate() != null ? r.getDate().toString() : "");
                row.createCell(3).setCellValue(r.getStatus() != null ? r.getStatus() : "-");
                row.createCell(4).setCellValue(r.getCheckIn() != null ? r.getCheckIn().toString() : "-");
                row.createCell(5).setCellValue(r.getCheckOut() != null ? r.getCheckOut().toString() : "-");
                row.createCell(6).setCellValue(r.getWorkingHours() != null ? r.getWorkingHours().toString() : "-");
                row.createCell(7).setCellValue(r.getSickLeaveDays() != null ? formatDays(r.getSickLeaveDays()) : "-");
                row.createCell(8).setCellValue(r.getAnnualLeaveDays() != null ? formatDays(r.getAnnualLeaveDays()) : "-");
                row.createCell(9).setCellValue(r.getWfhDays() != null ? r.getWfhDays().toPlainString() : "-");
                row.createCell(10).setCellValue(r.getPermissionMinutes() != null ? formatMinutes(r.getPermissionMinutes()) : "-");
                row.createCell(11).setCellValue(r.getPunchRecords() != null ? r.getPunchRecords() : "");
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private double calculateDaysOnDate(LeaveApplication leave, LocalDate date) {
        boolean isStart = date.equals(leave.getStartDate());
        boolean isEnd   = date.equals(leave.getEndDate());
        if (isStart && leave.getStartDateHalfDayType() != null) return 0.5;
        if (isEnd && !isStart && leave.getEndDateHalfDayType() != null) return 0.5;
        return 1.0;
    }

    private BigDecimal resolveWfhDays(LocalDate date, List<WfhApplication> wfhList) {
        return wfhList.stream()
                .filter(w -> !w.getStartDate().isAfter(date)
                        && !w.getEndDate().isBefore(date))
                .map(WfhApplication::getTotalDays)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String formatDays(double days) {
        return days == 0.5 ? "0.5" : String.valueOf((int) days);
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0 && m > 0) return h + "h " + m + "m";
        if (h > 0) return h + "h";
        return m + "m";
    }
}
