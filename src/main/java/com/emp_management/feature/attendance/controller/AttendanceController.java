package com.emp_management.feature.attendance.controller;

import com.emp_management.feature.attendance.dto.*;
import com.emp_management.feature.attendance.service.AttendanceReportService;
import com.emp_management.feature.attendance.service.AttendanceService;
import com.emp_management.feature.employee.dto.NameDto;
import com.emp_management.feature.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/attendance")
public class AttendanceController {

    private final AttendanceService       service;
    private final EmployeeService         empService;
    private final AttendanceReportService reportService;  // ← NEW

    public AttendanceController(
            AttendanceService service,
            EmployeeService empService,
            AttendanceReportService reportService) {       // ← NEW
        this.service       = service;
        this.empService    = empService;
        this.reportService = reportService;
    }

    // ── All existing endpoints UNCHANGED ─────────────────────────

    @GetMapping("/{empId}")
    public Page<AttendanceCalendarDTO> getEmployeeAttendanceByRange(
            @PathVariable String empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getEmployeeAttendanceByRange(empId, fromDate, toDate, page, size);
    }

    @GetMapping("/employee/{empId}")
    public List<AttendanceCalendarDTO> getEmployeeAttendance(
            @PathVariable String empId,
            @RequestParam int year,
            @RequestParam int month) {
        return service.getEmployeeMonthly(empId, year, month);
    }

    @GetMapping("/download/excel/{employeeId}")
    public ResponseEntity<InputStreamResource> downloadExcel(
            @PathVariable String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate start = (fromDate != null) ? fromDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end   = (toDate   != null) ? toDate   : LocalDate.now();

        List<AttendanceCalendarDTO> records =
                service.getEmployeeAttendanceByRange(employeeId, start, end, 0, 2000).getContent();
        NameDto empName = empService.getEmployeeName(employeeId);
        ByteArrayInputStream in =
                service.exportAttendanceToExcel(records, empName.getEmpName(), employeeId);

        String fileName = "Attendance_" + employeeId + "_" + start + "_to_" + end + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/daily")
    public List<AttendanceDetailDTO> getDailyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getDailyAttendance(date);
    }

    @GetMapping("/all")
    public Page<AttendanceDetailDTO> getAllEmployeesAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getAllEmployeesAttendance(fromDate, toDate, status, page, size);
    }

    @GetMapping("/employee/{empId}/punch-records")
    public AttendanceCalendarDTO getPunchRecords(
            @PathVariable String empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getPunchRecords(empId, date);
    }

    @GetMapping("/team/{reportingId}")
    public Page<AttendanceDetailDTO> getTeamAttendance(
            @PathVariable String reportingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getTeamAttendance(reportingId, fromDate, toDate, status, page, size);
    }

    @PostMapping("/download/team/{managerId}")
    public ResponseEntity<InputStreamResource> downloadTeamExcel(
            @PathVariable String managerId,
            @RequestBody AttendanceExportRequest request) {
        List<AttendanceDetailDTO> records = service.getTeamAttendanceExportData(
                request.getEmpIds(), request.getFromDate(), request.getToDate());
        ByteArrayInputStream in = service.exportTeamAttendanceToExcel(records);
        String fileName = "Team_Attendance_" + managerId + "_"
                + request.getFromDate() + "_to_" + request.getToDate() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping("/download/selection")
    public ResponseEntity<InputStreamResource> downloadSelectedEmployeesExcel(
            @Valid @RequestBody AttendanceExportRequest request) {
        if (request.getEmpIds() == null || request.getEmpIds().isEmpty()) {
            throw new IllegalArgumentException("Employee list cannot be empty");
        }
        List<AttendanceDetailDTO> records = service.getTeamAttendanceExportData(
                request.getEmpIds(), request.getFromDate(), request.getToDate());
        ByteArrayInputStream in = service.exportTeamAttendanceToExcel(records);
        String fileName = "Attendance_Report_"
                + request.getFromDate() + "_to_" + request.getToDate() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/download/all")
    public ResponseEntity<InputStreamResource> downloadAllAttendanceExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status) {
        List<AttendanceDetailDTO> records =
                service.getAllAttendanceExportData(fromDate, toDate, status);
        ByteArrayInputStream in = service.exportTeamAttendanceToExcel(records);
        String fileName = "Organization_Attendance_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // ════════════════════════════════════════════════════════════════
    // NEW: Rich report — attendance + leave + WFH + permission
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/attendance/report/{empId}?fromDate=&toDate=
     * Returns date-wise rows with all data combined.
     * Works even if attendance_summary rows are missing.
     */
    @GetMapping("/report/{empId}")
    public ResponseEntity<List<AttendanceReportRowDTO>> getEmployeeReport(
            @PathVariable String empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        NameDto nameDto = empService.getEmployeeName(empId);
        String name = nameDto != null ? nameDto.getEmpName() : empId;

        List<AttendanceReportRowDTO> rows =
                reportService.getEmployeeReport(empId, name, fromDate, toDate);
        return ResponseEntity.ok(rows);
    }

    /**
     * GET /api/v1/attendance/report/{empId}/download?fromDate=&toDate=
     * Excel download with all columns including Leave, WFH, Permission.
     */
    @GetMapping("/report/{empId}/download")
    public ResponseEntity<InputStreamResource> downloadEmployeeReport(
            @PathVariable String empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        ByteArrayInputStream in = reportService.exportToExcel15Col(empId, fromDate, toDate);

        String fileName = "Attendance_" + empId + "_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}
