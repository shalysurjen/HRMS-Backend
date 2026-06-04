package com.emp_management.feature.leave.annual.controller;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.leave.annual.dto.LeaveApplicationResponseDTO;
import com.emp_management.feature.leave.annual.dto.LeaveApplicationWithAttachmentsDto;
import com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO;
import com.emp_management.feature.leave.annual.dto.LeaveResponse;
import com.emp_management.feature.leave.annual.entity.LeaveApplication;
import com.emp_management.feature.leave.annual.entity.LeaveAttachment;
import com.emp_management.feature.leave.annual.entity.LeaveType;
import com.emp_management.feature.leave.annual.repository.LeaveTypeRepository;
import com.emp_management.feature.leave.annual.service.LeaveApplicationService;
import com.emp_management.feature.leave.annual.service.LeaveAttachmentService;
import com.emp_management.feature.leave.annual.service.LeaveExportService;
import com.emp_management.feature.leave.annual.dto.LeaveExportRequest;
import com.emp_management.shared.enums.HalfDayType;
import com.emp_management.shared.enums.RequestStatus;
import com.emp_management.shared.exceptions.BadRequestException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.io.ByteArrayInputStream;
import org.springframework.core.io.InputStreamResource;

@RestController
@RequestMapping("/v1/leaves")
public class LeaveApplicationController {

    private final LeaveApplicationService leaveApplicationService;
    private final LeaveAttachmentService  leaveAttachmentService;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveExportService  leaveExportService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public LeaveApplicationController(LeaveApplicationService leaveApplicationService,
                                      LeaveAttachmentService leaveAttachmentService,
                                      EmployeeRepository employeeRepository,
                                      LeaveTypeRepository leaveTypeRepository,
                                      LeaveExportService leaveExportService) {
        this.leaveApplicationService = leaveApplicationService;
        this.leaveAttachmentService  = leaveAttachmentService;
        this.employeeRepository      = employeeRepository;
        this.leaveTypeRepository     = leaveTypeRepository;
        this.leaveExportService      = leaveExportService;
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeaveResponse> applyLeave(
            @RequestParam String employeeId,              // now String (emp_code)
            @RequestParam String leaveTypeName,               // FK to LeaveType entity
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String reason,
            @RequestParam(required = false) String halfDayType,
            @RequestParam(required = false) String startDateHalfDayType,
            @RequestParam(required = false) String endDateHalfDayType,
            @RequestParam(defaultValue = "false") boolean isAppointment,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        Employee employee = employeeRepository.findByEmpId(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found: " + employeeId));

        LeaveType leaveType = leaveTypeRepository.findByLeaveType(leaveTypeName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid leaveTypeId: " + leaveTypeName));

        if (startDate == null) throw new BadRequestException("Start date is required");

        LocalDate today   = LocalDate.now(IST);
        String   typeName = leaveType.getLeaveType().toUpperCase();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        // ── Date rules ────────────────────────────────────────────
        if ("SICK".equals(typeName)) {
            if (startDate.isBefore(firstDayOfMonth))
                throw new BadRequestException("Sick leave can only be applied within the current month.");
            if (startDate.isAfter(today)) {
                if (!isAppointment)
                    throw new BadRequestException(
                            "Sick leave for future dates requires isAppointment=true.");
                if (files == null || files.length == 0)
                    throw new BadRequestException(
                            "An attachment (appointment proof) is required for future sick leave.");
            }
        } else if (!"MATERNITY".equals(typeName) && !"PATERNITY".equals(typeName)) {
            if (startDate.isBefore(firstDayOfMonth))
                throw new BadRequestException("Leave can only be applied within the current month.");
        }

        LeaveApplication leave = new LeaveApplication();
        leave.setEmployee(employee);
        leave.setLeaveType(leaveType);
        leave.setStartDate(startDate);
        leave.setEndDate(endDate);
        leave.setReason(reason);
        leave.setIsAppointment(isAppointment);

        // ── Half-day parsing ──────────────────────────────────────
        if (startDateHalfDayType != null && !startDateHalfDayType.isBlank()) {
            try { leave.setStartDateHalfDayType(HalfDayType.valueOf(startDateHalfDayType.toUpperCase())); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid startDateHalfDayType: " + startDateHalfDayType); }
        }
        if (endDateHalfDayType != null && !endDateHalfDayType.isBlank()) {
            try { leave.setEndDateHalfDayType(HalfDayType.valueOf(endDateHalfDayType.toUpperCase())); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid endDateHalfDayType: " + endDateHalfDayType); }
        }
        // Backward-compatible: single halfDayType param
        if (halfDayType != null && !halfDayType.isBlank()
                && leave.getStartDateHalfDayType() == null
                && leave.getEndDateHalfDayType()   == null) {
            try {
                HalfDayType hdt = HalfDayType.valueOf(halfDayType.toUpperCase());
                leave.setStartDateHalfDayType(hdt);
                leave.setHalfDayType(hdt);
            } catch (IllegalArgumentException e) { throw new BadRequestException("Invalid halfDayType: " + halfDayType); }
        }

        LeaveResponse response = leaveApplicationService.applyLeave(leave);

        if (response.getLeaveApplication() != null && files != null && files.length > 0) {
            try {
                leaveAttachmentService.uploadAttachments(
                        response.getLeaveApplication().getId(), employeeId, files);
            } catch (Exception e) {
                response.setWarning("Leave applied but file upload failed: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{leaveId}/attachments")
    public ResponseEntity<List<LeaveAttachment>> getAttachments(@PathVariable Long leaveId) {
        return ResponseEntity.ok(leaveAttachmentService.getAttachments(leaveId));
    }

    @GetMapping("/attachments/download/{filename}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename) {
        try {
            Path filePath   = leaveAttachmentService.getFilePath(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error downloading file");
        }
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<String> deleteAttachment(
            @PathVariable Long attachmentId,
            @RequestParam String employeeId) {
        leaveAttachmentService.deleteAttachment(attachmentId, employeeId);
        return ResponseEntity.ok("Attachment deleted successfully");
    }

    @GetMapping("/{id}")
    public LeaveApplicationWithAttachmentsDto getLeaveById(@PathVariable Long id) {
        return leaveApplicationService.getLeaveById(id);
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveApplicationResponseDTO> getEmployeeLeaves(
            @PathVariable String employeeId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer year) {
        return leaveApplicationService.getLeavesByEmployee(employeeId);
    }

    @PutMapping("/{id}")
    public LeaveResponse updateLeave(
            @PathVariable Long id,
            @RequestParam String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String startDateHalfDayType,
            @RequestParam(required = false) String endDateHalfDayType) {
        return leaveApplicationService.updateLeave(
                id, employeeId, startDate, endDate, reason,
                startDateHalfDayType, endDateHalfDayType);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<String> cancelEmployeeLeave(
            @PathVariable Long id,
            @RequestParam String employeeId) {
        leaveApplicationService.cancelEmployeeLeave(id, employeeId);
        return ResponseEntity.ok("Leave cancelled successfully.");
    }

    // ════════════════════════════════════════════════════════════════
    //  LEAVE EXPORT ENDPOINTS
    // ════════════════════════════════════════════════════════════════

    /**
     * GET /v1/leaves/export?month=5&year=2026  → Excel for that month
     * GET /v1/leaves/export                     → Excel for all time
     *
     * Columns: Application Created Date | Employee ID | Employee Name | Leave Type |
     *          Start Date | End Date | Start of the Day | No. of Days | Leave Year |
     *          First Approver | First Approval Date | First Approval Decision |
     *          Second Approver | Second Approval Date | Second Approval Decision
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportLeaves(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        ByteArrayInputStream excelStream;
        String filename;

        if (month != null && year != null) {
            excelStream = leaveExportService.exportByMonth(month, year);
            String monthName = java.time.Month.of(month).name();
            filename = "Employee_Leave_Export_"
                    + monthName.charAt(0)
                    + monthName.substring(1).toLowerCase()
                    + year + ".xlsx";
        } else {
            excelStream = leaveExportService.exportAll();
            filename = "Employee_Leave_Export_All.xlsx";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(excelStream));
    }

    /** JSON preview — all employees (Admin/CFO) */
    @GetMapping("/export/all")
    public ResponseEntity<List<LeaveExportRowDTO>> exportAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(leaveApplicationService.getLeaveExportAll(fromDate, toDate));
    }

    /** JSON preview — team members (Manager) */
    @PostMapping("/export/team")
    public ResponseEntity<List<LeaveExportRowDTO>> exportTeam(
            @RequestBody LeaveExportRequest request) {
        return ResponseEntity.ok(leaveApplicationService.getLeaveExportForTeam(
                request.getEmpIds(), request.getFromDate(), request.getToDate()));
    }

    /** Excel download — all employees */
    @GetMapping("/export/download/all")
    public ResponseEntity<InputStreamResource> downloadAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<LeaveExportRowDTO> rows = leaveApplicationService.getLeaveExportAll(fromDate, toDate);
        ByteArrayInputStream in = leaveApplicationService.exportLeaveToExcel(rows);
        String fileName = "Employee_Leave_Export_" + fromDate + "_to_" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /** Excel download — team members */
    @PostMapping("/export/download/team")
    public ResponseEntity<InputStreamResource> downloadTeam(
            @RequestBody LeaveExportRequest request) {
        List<LeaveExportRowDTO> rows = leaveApplicationService.getLeaveExportForTeam(
                request.getEmpIds(), request.getFromDate(), request.getToDate());
        ByteArrayInputStream in = leaveApplicationService.exportLeaveToExcel(rows);
        String fileName = "Team_Leave_Export_" + request.getFromDate() + "_to_" + request.getToDate() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}