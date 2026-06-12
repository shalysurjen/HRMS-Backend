package com.emp_management.feature.attendance.service;

import com.emp_management.feature.attendance.dto.AttendanceDetailedRowDTO;
import com.emp_management.feature.attendance.entity.AttendanceSummary;
import com.emp_management.feature.attendance.repository.AttendanceSummaryRepository;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.leave.annual.entity.LeaveApplication;
import com.emp_management.feature.leave.annual.repository.LeaveApplicationRepository;
import com.emp_management.feature.leave.carryforward.entity.CarryForwardLeaveApplication;
import com.emp_management.feature.leave.carryforward.repository.CarryForwardLeaveApplicationRepository;
import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.feature.permission.repository.PermissionRepository;
import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.feature.wfh.repository.WfhApplicationRepository;
import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.RequestStatus;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceDetailedService {

    private final AttendanceSummaryRepository            attendanceRepo;
    private final LeaveApplicationRepository             leaveRepo;
    private final CarryForwardLeaveApplicationRepository cfLeaveRepo;
    private final WfhApplicationRepository               wfhRepo;
    private final PermissionRepository                   permissionRepo;
    private final EmployeeRepository                     employeeRepo;

    public AttendanceDetailedService(
            AttendanceSummaryRepository attendanceRepo,
            LeaveApplicationRepository leaveRepo,
            CarryForwardLeaveApplicationRepository cfLeaveRepo,
            WfhApplicationRepository wfhRepo,
            PermissionRepository permissionRepo,
            EmployeeRepository employeeRepo) {
        this.attendanceRepo = attendanceRepo;
        this.leaveRepo      = leaveRepo;
        this.cfLeaveRepo    = cfLeaveRepo;
        this.wfhRepo        = wfhRepo;
        this.permissionRepo = permissionRepo;
        this.employeeRepo   = employeeRepo;
    }

    @Transactional(readOnly = true)
    public Page<AttendanceDetailedRowDTO> getDetailedRows(
            String empId, LocalDate fromDate, LocalDate toDate, int page, int size) {

        // 1. All dates in range descending
        List<LocalDate> allDates = new ArrayList<>();
        for (LocalDate d = toDate; !d.isBefore(fromDate); d = d.minusDays(1)) {
            allDates.add(d);
        }

        // 2. attendance_summary → map by date
        List<AttendanceSummary> summaries =
                attendanceRepo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        empId, fromDate, toDate);
        Map<LocalDate, AttendanceSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(
                        AttendanceSummary::getAttendanceDate, s -> s, (a, b) -> a));

        // 3. Employee name — always from employees table
        //    Old: summaries.get(0).getEmployeeName() → empty if no attendance rows → wrong name
        //    Fix: employeeRepo.findByEmpId(empId) → always correct
        String resolvedEmpName = employeeRepo.findByEmpId(empId)
                .map(e -> e.getName() != null ? e.getName() : "")
                .orElseGet(() -> {
                    if (!summaries.isEmpty()
                            && summaries.get(0).getEmployeeName() != null) {
                        return summaries.get(0).getEmployeeName();
                    }
                    return "";
                });

        // 4. carry_forward_leave_application — APPROVED, overlapping range
        List<CarryForwardLeaveApplication> cfLeaves =
                cfLeaveRepo.findApprovedByEmpIdAndDateRange(empId, fromDate, toDate);

        // 5. leave_application — APPROVED + PENDING + REJECTED, overlapping range
        //    REJECTED included so that L1-approved / L2-rejected leaves still show in report
        List<LeaveApplication> leaves =
                leaveRepo.findForDetailedReport(
                        empId,
                        List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.REJECTED),
                        fromDate, toDate);

        // 6. wfh_application — APPROVED + PENDING + REJECTED, overlapping range
        List<WfhApplication> wfhList =
                wfhRepo.findApprovedAndPendingByEmpIdAndDateRange(
                        empId,
                        List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.REJECTED),
                        fromDate, toDate);

        // 7. permission_application — APPROVED + PENDING + REJECTED, exact date
        List<Permission> permissions =
                permissionRepo.findApprovedAndPendingByEmpIdAndDateRange(
                        empId,
                        List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.REJECTED),
                        fromDate, toDate);

        // 8. Build expanded list of "slots" — one slot per row in the report.
        //    Every permission gets its own row always.
        //    If a date has leave/wfh/summary but NO permissions → 1 row.
        //    If a date has N permissions → N rows (each permission gets its own row,
        //    and each row also carries the leave/wfh/summary data for that date).
        record Slot(LocalDate date, Permission permissionSlot) {}

        List<Slot> allSlots = new ArrayList<>();
        for (LocalDate d : allDates) {
            boolean hasLeave   = hasLeaveOnDate(d, leaves);
            boolean hasCfLeave = hasCfLeaveOnDate(d, cfLeaves);
            boolean hasWfh     = hasWfhOnDate(d, wfhList);
            boolean hasSummary = summaryMap.containsKey(d);
            List<Permission> dayPerms = permissions.stream()
                    .filter(p -> d.equals(p.getPermissionDate()))
                    .collect(Collectors.toList());

            if (!dayPerms.isEmpty()) {
                // One row per permission on this date
                for (Permission p : dayPerms) {
                    allSlots.add(new Slot(d, p));
                }
            } else if (hasLeave || hasCfLeave || hasWfh || hasSummary) {
                // No permissions — single row for leave/wfh/attendance
                allSlots.add(new Slot(d, null));
            }
        }

        // 9. Paginate over slots
        int total   = allSlots.size();
        int fromIdx = page * size;
        int toIdx   = Math.min(fromIdx + size, total);
        List<Slot> pageSlots = fromIdx >= total
                ? Collections.emptyList()
                : allSlots.subList(fromIdx, toIdx);

        // 10. Build rows
        final String finalEmpName = resolvedEmpName;
        List<AttendanceDetailedRowDTO> rows = new ArrayList<>();
        for (Slot slot : pageSlots) {
            AttendanceSummary s = summaryMap.get(slot.date());
            if (slot.permissionSlot() != null) {
                // Single-permission row — pass only this one permission,
                // and set permissionOnly=true so Application Status comes from
                // this permission alone (not overridden by leave/wfh on same date)
                rows.add(buildRow(slot.date(), s, empId, finalEmpName,
                        leaves, cfLeaves, wfhList,
                        List.of(slot.permissionSlot()), true));
            } else {
                rows.add(buildRow(slot.date(), s, empId, finalEmpName,
                        leaves, cfLeaves, wfhList, permissions, false));
            }
        }

        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }

    // ═══════════════════════════════════════════════════════════
    // ROW BUILDER
    // ═══════════════════════════════════════════════════════════

    private AttendanceDetailedRowDTO buildRow(
            LocalDate date,
            AttendanceSummary s,
            String fallbackEmpId,
            String fallbackEmpName,
            List<LeaveApplication>              leaves,
            List<CarryForwardLeaveApplication>  cfLeaves,
            List<WfhApplication>                wfhList,
            List<Permission>                    permissions,
            boolean                             permissionOnly) {

        AttendanceDetailedRowDTO row = new AttendanceDetailedRowDTO();

        // From attendance_summary
        row.setEmpId(s != null ? s.getEmployeeId() : fallbackEmpId);
        row.setEmpName(s != null && s.getEmployeeName() != null
                ? s.getEmployeeName() : fallbackEmpName);
        row.setDate(date.toString());
        row.setCheckIn(s != null ? formatTime(s.getCheckIn()) : null);
        row.setCheckOut(s != null ? formatTime(s.getCheckOut()) : null);
        row.setWorkHours(s != null ? formatDuration(s.getWorkingHours()) : null);
        row.setPunchRecords(s != null ? s.getPunchRecords() : null);

        // From carry_forward_leave_application (APPROVED, overlapping date)
        double cfDays = cfLeaves.stream()
                .filter(cf -> cf.getStartDate() != null && cf.getEndDate() != null
                        && !date.isBefore(cf.getStartDate())
                        && !date.isAfter(cf.getEndDate()))
                .mapToDouble(cf -> cf.getDays() != null
                        ? cf.getDays().doubleValue() : 0.0)
                .sum();
        row.setCfLeaveDays(cfDays > 0 ? cfDays : null);

        // From leave_application (APPROVED + PENDING, overlapping date)
        double glDays = 0.0, slDays = 0.0, lopDays = 0.0;
        String leaveStatus = null;

        for (LeaveApplication l : leaves) {
            if (l.getStartDate() == null || l.getEndDate() == null) continue;
            if (date.isBefore(l.getStartDate()) || date.isAfter(l.getEndDate())) continue;

            if (leaveStatus == null) {
                leaveStatus = resolveLeaveStatus(l);
            }

            // Show days for APPROVED, PENDING, and REJECTED (so rejected leaves
            // still appear with day count + REJECTED status in the report)
            if (l.getStatus() == RequestStatus.APPROVED
                    || l.getStatus() == RequestStatus.PENDING
                    || l.getStatus() == RequestStatus.REJECTED) {

                String typeName = l.getLeaveType() != null
                        ? l.getLeaveType().getLeaveType().toUpperCase() : "";

                if (typeName.contains("SICK") || typeName.equals("SL")) {
                    slDays += l.getDays() != null ? l.getDays().doubleValue() : 0.0;
                } else {
                    glDays += l.getDays() != null ? l.getDays().doubleValue() : 0.0;
                }
                // LOP only applies on APPROVED
                if (l.getStatus() == RequestStatus.APPROVED) {
                    lopDays += l.getLossOfPayApplied() != null
                            ? l.getLossOfPayApplied() : 0.0;
                }
            }
        }
        row.setGlDays(glDays   > 0 ? glDays   : null);
        row.setSlDays(slDays   > 0 ? slDays   : null);
        row.setLopDays(lopDays > 0 ? lopDays  : null);

        // From wfh_application (APPROVED, overlapping date) — 1 day per date
        double wfhDays = 0.0;
        String wfhStatus = null;
        for (WfhApplication w : wfhList) {
            if (w.getStartDate() == null || w.getEndDate() == null) continue;
            if (date.isBefore(w.getStartDate()) || date.isAfter(w.getEndDate())) continue;
            if (wfhStatus == null) {
                wfhStatus = resolveWfhStatus(w);
            }
            // Count days for APPROVED, PENDING, and REJECTED
            if (w.getStatus() == RequestStatus.APPROVED
                    || w.getStatus() == RequestStatus.PENDING
                    || w.getStatus() == RequestStatus.REJECTED) {
                // Use totalDays when available (handles half-day WFH correctly)
                if (w.getTotalDays() != null) {
                    wfhDays += w.getTotalDays().doubleValue();
                } else {
                    wfhDays += 1.0;
                }
            }
        }
        row.setWfhDays(wfhDays > 0 ? wfhDays : null);

        // From permission_application (APPROVED, exact date) — duration_minutes / 60
        double permMinutes = 0.0;
        String permStatus  = null;
        for (Permission p : permissions) {
            if (!date.equals(p.getPermissionDate())) continue;
            if (permStatus == null) {
                permStatus = resolvePermissionStatus(p);
            }
            // Count minutes for APPROVED, PENDING, and REJECTED
            if (p.getStatus() == RequestStatus.APPROVED
                    || p.getStatus() == RequestStatus.PENDING
                    || p.getStatus() == RequestStatus.REJECTED) {
                permMinutes += p.getDurationMinutes() != null
                        ? p.getDurationMinutes() : 0;
            }
        }
        row.setPermissionHours(permMinutes > 0 ? formatMinutes((long) permMinutes) : null);

        // Attendance Status — always from attendance_summary (PRESENT / ABSENT / WEEKEND / HOLIDAY etc.)
        row.setAttendanceStatus(
                s != null && s.getAttendanceStatus() != null
                        ? s.getAttendanceStatus() : "");

        // Application Status — from leave / wfh / permission approval flow
        // If this is a dedicated permission row (permissionOnly=true), always use
        // permStatus so the permission's own PENDING/APPROVED/REJECTED is shown,
        // not overridden by a leave or WFH that also happens to be on the same date.
        if (permissionOnly) {
            row.setApprovalStatus(permStatus != null ? permStatus : "");
        } else if (leaveStatus != null) {
            row.setApprovalStatus(leaveStatus);
        } else if (wfhStatus != null) {
            row.setApprovalStatus(wfhStatus);
        } else if (permStatus != null) {
            row.setApprovalStatus(permStatus);
        } else {
            row.setApprovalStatus("");
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════
    // DATE PRESENCE CHECKS
    // ═══════════════════════════════════════════════════════════

    private boolean hasLeaveOnDate(LocalDate d, List<LeaveApplication> list) {
        return list.stream().anyMatch(l ->
                l.getStartDate() != null && l.getEndDate() != null
                        && !d.isBefore(l.getStartDate())
                        && !d.isAfter(l.getEndDate()));
    }

    private boolean hasCfLeaveOnDate(LocalDate d,
                                     List<CarryForwardLeaveApplication> list) {
        return list.stream().anyMatch(c ->
                c.getStartDate() != null && c.getEndDate() != null
                        && !d.isBefore(c.getStartDate())
                        && !d.isAfter(c.getEndDate()));
    }

    private boolean hasWfhOnDate(LocalDate d, List<WfhApplication> list) {
        return list.stream().anyMatch(w ->
                w.getStartDate() != null && w.getEndDate() != null
                        && !d.isBefore(w.getStartDate())
                        && !d.isAfter(w.getEndDate()));
    }

    private boolean hasPermissionOnDate(LocalDate d, List<Permission> list) {
        return list.stream().anyMatch(p -> d.equals(p.getPermissionDate()));
    }

    // ═══════════════════════════════════════════════════════════
    // STATUS RESOLVERS
    // Returns: LEVEL 1 PENDING / LEVEL 2 PENDING / APPROVED / REJECTED
    // ═══════════════════════════════════════════════════════════

    private String resolveLeaveStatus(LeaveApplication l) {
        if (l.getStatus() == RequestStatus.REJECTED) {
            // L1 approved, L2 rejected → show "LEVEL 2 REJECTED"
            if (l.getFirstApproverDecision() == RequestStatus.APPROVED) {
                return "LEVEL 2 REJECTED";
            }
            return "REJECTED";
        }
        if (l.getStatus() == RequestStatus.APPROVED) return "APPROVED";
        if (l.getCurrentApprovalLevel() == ApprovalLevel.SECOND_APPROVER)
            return "LEVEL 2 PENDING";
        return "LEVEL 1 PENDING";
    }

    private String resolveWfhStatus(WfhApplication w) {
        if (w.getStatus() == RequestStatus.REJECTED) {
            // L1 approved, L2 rejected → show "LEVEL 2 REJECTED"
            if (w.getFirstApproverDecision() == RequestStatus.APPROVED) {
                return "LEVEL 2 REJECTED";
            }
            return "REJECTED";
        }
        if (w.getStatus() == RequestStatus.APPROVED) return "APPROVED";
        if (w.getCurrentApprovalLevel() == ApprovalLevel.SECOND_APPROVER)
            return "LEVEL 2 PENDING";
        return "LEVEL 1 PENDING";
    }

    private String resolvePermissionStatus(Permission p) {
        if (p.getStatus() == RequestStatus.REJECTED) {
            // L1 approved, L2 rejected → show "LEVEL 2 REJECTED"
            if (p.getFirstApproverDecision() == RequestStatus.APPROVED) {
                return "LEVEL 2 REJECTED";
            }
            return "REJECTED";
        }
        if (p.getStatus() == RequestStatus.APPROVED) return "APPROVED";
        if (p.getCurrentApprovalLevel() == ApprovalLevel.SECOND_APPROVER)
            return "LEVEL 2 PENDING";
        return "LEVEL 1 PENDING";
    }

    // ═══════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════

    private String formatTime(LocalTime t) {
        if (t == null) return null;
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    // Formats LocalTime as "Xh Ym" — used for workHours duration display
    private String formatDuration(LocalTime t) {
        if (t == null) return null;
        int h = t.getHour();
        int m = t.getMinute();
        if (h == 0 && m == 0) return null;
        return h + "h " + m + "m";
    }

    // Formats total minutes as "Xh Ym" — used for permission hours display
    private String formatMinutes(long totalMinutes) {
        if (totalMinutes <= 0) return null;
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        return h + "h " + m + "m";
    }

    // ═══════════════════════════════════════════════════════════
    // EXCEL EXPORT — mirrors UI table exactly
    // Row 1 : From Date / To Date info
    // Row 2 : blank separator
    // Row 3 : column headers
    // Row 4+: data rows (all pages fetched at once)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportDetailedToExcel(
            String empId, LocalDate fromDate, LocalDate toDate) {

        // Fetch all rows (no pagination) for full export
        Page<AttendanceDetailedRowDTO> allRows =
                getDetailedRows(empId, fromDate, toDate, 0, Integer.MAX_VALUE);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance Report");

            // ── Styles ────────────────────────────────────────
            // Date info style
            CellStyle infoStyle = workbook.createCellStyle();
            Font infoFont = workbook.createFont();
            infoFont.setBold(true);
            infoFont.setFontHeightInPoints((short) 11);
            infoStyle.setFont(infoFont);

            // Header style (same as existing exports)
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // ── Row 0: From Date / To Date ────────────────────
            Row dateRow = sheet.createRow(0);
            Cell fromCell = dateRow.createCell(0);
            fromCell.setCellValue("From Date: " + fromDate.toString());
            fromCell.setCellStyle(infoStyle);
            Cell toCell = dateRow.createCell(2);
            toCell.setCellValue("To Date: " + toDate.toString());
            toCell.setCellStyle(infoStyle);

            // ── Row 1: blank separator ────────────────────────
            sheet.createRow(1);

            // ── Row 2: Column headers ─────────────────────────
            String[] headers = {
                    "Emp ID", "Emp Name", "Date",
                    "Check In", "Check Out", "Work Hours",
                    "CF Leave (Days)", "GL (Days)", "SL (Days)",
                    "WFH (Days)", "Permission (Hrs)", "LOP (Days)",
                    "Attendance Status", "Application Status", "Punches"
            };
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Row 3+: Data rows ─────────────────────────────
            int rowIdx = 3;
            for (AttendanceDetailedRowDTO r : allRows.getContent()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nvl(r.getEmpId()));
                row.createCell(1).setCellValue(nvl(r.getEmpName()));
                row.createCell(2).setCellValue(nvl(r.getDate()));
                row.createCell(3).setCellValue(nvl(r.getCheckIn()));
                row.createCell(4).setCellValue(nvl(r.getCheckOut()));
                row.createCell(5).setCellValue(nvl(r.getWorkHours()));
                row.createCell(6).setCellValue(fmtDays(r.getCfLeaveDays()));
                row.createCell(7).setCellValue(fmtDays(r.getGlDays()));
                row.createCell(8).setCellValue(fmtDays(r.getSlDays()));
                row.createCell(9).setCellValue(fmtDays(r.getWfhDays()));
                row.createCell(10).setCellValue(nvl(r.getPermissionHours()));
                row.createCell(11).setCellValue(fmtDays(r.getLopDays()));
                row.createCell(12).setCellValue(nvl(r.getAttendanceStatus()));
                row.createCell(13).setCellValue(nvl(r.getApprovalStatus()));
                row.createCell(14).setCellValue(nvl(r.getPunchRecords()));
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate detailed attendance Excel", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // BULK EXCEL EXPORT — multiple employees, same 15-column format
    // All employees in one sheet, continuous rows
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportBulkDetailedToExcel(
            List<String> empIds, LocalDate fromDate, LocalDate toDate) {

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance Report");

            // ── Styles ────────────────────────────────────────
            CellStyle infoStyle = workbook.createCellStyle();
            Font infoFont = workbook.createFont();
            infoFont.setBold(true);
            infoFont.setFontHeightInPoints((short) 11);
            infoStyle.setFont(infoFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // ── Row 0: From Date / To Date ────────────────────
            Row dateRow = sheet.createRow(0);
            Cell fromCell = dateRow.createCell(0);
            fromCell.setCellValue("From Date: " + fromDate.toString());
            fromCell.setCellStyle(infoStyle);
            Cell toCell = dateRow.createCell(2);
            toCell.setCellValue("To Date: " + toDate.toString());
            toCell.setCellStyle(infoStyle);

            // ── Row 1: blank separator ────────────────────────
            sheet.createRow(1);

            // ── Row 2: Column headers ─────────────────────────
            String[] headers = {
                    "Emp ID", "Emp Name", "Date",
                    "Check In", "Check Out", "Work Hours",
                    "CF Leave (Days)", "GL (Days)", "SL (Days)",
                    "WFH (Days)", "Permission (Hrs)", "LOP (Days)",
                    "Attendance Status", "Application Status", "Punches"
            };
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Row 3+: Data rows for each employee ───────────
            int rowIdx = 3;
            for (String empId : empIds) {
                Page<AttendanceDetailedRowDTO> empRows =
                        getDetailedRows(empId, fromDate, toDate, 0, Integer.MAX_VALUE);
                for (AttendanceDetailedRowDTO r : empRows.getContent()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(nvl(r.getEmpId()));
                    row.createCell(1).setCellValue(nvl(r.getEmpName()));
                    row.createCell(2).setCellValue(nvl(r.getDate()));
                    row.createCell(3).setCellValue(nvl(r.getCheckIn()));
                    row.createCell(4).setCellValue(nvl(r.getCheckOut()));
                    row.createCell(5).setCellValue(nvl(r.getWorkHours()));
                    row.createCell(6).setCellValue(fmtDays(r.getCfLeaveDays()));
                    row.createCell(7).setCellValue(fmtDays(r.getGlDays()));
                    row.createCell(8).setCellValue(fmtDays(r.getSlDays()));
                    row.createCell(9).setCellValue(fmtDays(r.getWfhDays()));
                    row.createCell(10).setCellValue(nvl(r.getPermissionHours()));
                    row.createCell(11).setCellValue(fmtDays(r.getLopDays()));
                    row.createCell(12).setCellValue(nvl(r.getAttendanceStatus()));
                    row.createCell(13).setCellValue(nvl(r.getApprovalStatus()));
                    row.createCell(14).setCellValue(nvl(r.getPunchRecords()));
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate bulk detailed attendance Excel", e);
        }
    }

    /** Null-safe string helper */
    private String nvl(String val) {
        return val != null ? val : "";
    }

    /**
     * Format days: whole number → "1", "2"; half day → "0.5"
     * Matches frontend fmtDays() logic exactly.
     */
    private String fmtDays(Double val) {
        if (val == null || val == 0.0) return "";
        if (val % 1 == 0) return String.valueOf(val.intValue());
        return String.valueOf(val);
    }
}