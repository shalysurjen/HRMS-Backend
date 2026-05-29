package com.emp_management.feature.wfh.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.leave.annual.repository.LeaveApplicationRepository;
import com.emp_management.feature.notification.service.NotificationService;
import com.emp_management.feature.wfh.dto.WfhEditRequestDTO;
import com.emp_management.feature.wfh.dto.WfhRequestDTO;
import com.emp_management.feature.wfh.dto.WfhResponseDTO;
import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.feature.wfh.mapper.WfhMapper;
import com.emp_management.feature.wfh.repository.WfhApplicationRepository;
import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.Channel;
import com.emp_management.shared.enums.EventType;
import com.emp_management.shared.enums.HalfDayType;
import com.emp_management.shared.enums.RequestStatus;
import com.emp_management.shared.exceptions.BadRequestException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.net.MalformedURLException;

@Service
public class WfhService {

    private static final long MAX_BACKDATE_DAYS = 31;
    private static final String UPLOAD_DIR      = "uploads/wfh/";

    private final WfhApplicationRepository wfhRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public WfhService(WfhApplicationRepository wfhRepository,
                      LeaveApplicationRepository leaveRepository,
                      EmployeeRepository employeeRepository,
                      NotificationService notificationService) {
        this.wfhRepository      = wfhRepository;
        this.leaveRepository    = leaveRepository;
        this.employeeRepository  = employeeRepository;
        this.notificationService = notificationService;
    }

    // ═══════════════════════════════════════════════════════════════
    // APPLY WFH
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public WfhResponseDTO applyWfh(WfhRequestDTO request) {

        Employee employee = employeeRepository
                .findByEmpId(request.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee not found: " + request.getEmployeeId()));

        validateDates(request.getStartDate(), request.getEndDate());

        checkLeaveOverlap(request.getEmployeeId(), request.getStartDate(), request.getEndDate());
        checkWfhOverlap(request.getEmployeeId(), request.getStartDate(), request.getEndDate(), null);

        HalfDayType startHalf = parseHalfDay(request.getStartDateHalfDayType());
        HalfDayType endHalf   = parseHalfDay(request.getEndDateHalfDayType());

        BigDecimal totalDays = calculateDays(
                request.getStartDate(), request.getEndDate(), startHalf, endHalf);

        WfhApplication wfh = new WfhApplication();
        wfh.setEmployee(employee);
        wfh.setStartDate(request.getStartDate());
        wfh.setEndDate(request.getEndDate());
        wfh.setStartDateHalfDayType(startHalf);
        wfh.setEndDateHalfDayType(endHalf);
        wfh.setTotalDays(totalDays);
        wfh.setReason(request.getReason());
        wfh.setStatus(RequestStatus.PENDING);
        wfh.setCreatedBy(employee.getEmpId());
        wfh.setUpdatedBy(employee.getEmpId());

        saveAttachment(request.getAttachment(), wfh);
        setupApprovalChain(wfh, employee);

        if (wfh.getRequiredApprovalLevels() == 0) {
            wfh.setStatus(RequestStatus.APPROVED);
            wfh.setApprovedBy(employee.getEmpId());
            wfh.setApprovedRole(employee.getRole().getRoleName());
            WfhApplication saved = wfhRepository.save(wfh);
            notifyAdmin(saved, employee.getEmail(), "Auto-Approved");
            return WfhMapper.toDTO(saved);
        }

        WfhApplication saved = wfhRepository.save(wfh);
        notifyFirstApprover(saved, employee);
        notifyAdmin(saved, employee.getEmail(), "Pending Approval");
        return WfhMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════
    // EDIT WFH (only PENDING)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public WfhResponseDTO editWfh(Long wfhId, WfhEditRequestDTO request) {

        WfhApplication wfh = wfhRepository.findById(wfhId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "WFH application not found: " + wfhId));

        if (wfh.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING WFH requests can be edited. Current status: " + wfh.getStatus());
        }

        if (!wfh.getEmployeeId().equals(request.getEmployeeId())) {
            throw new BadRequestException(
                    "You are not authorized to edit this WFH request.");
        }

        validateDates(request.getStartDate(), request.getEndDate());

        checkLeaveOverlap(request.getEmployeeId(), request.getStartDate(), request.getEndDate());
        checkWfhOverlap(request.getEmployeeId(), request.getStartDate(), request.getEndDate(), wfhId);

        HalfDayType startHalf = parseHalfDay(request.getStartDateHalfDayType());
        HalfDayType endHalf   = parseHalfDay(request.getEndDateHalfDayType());

        BigDecimal totalDays = calculateDays(
                request.getStartDate(), request.getEndDate(), startHalf, endHalf);

        wfh.setStartDate(request.getStartDate());
        wfh.setEndDate(request.getEndDate());
        wfh.setStartDateHalfDayType(startHalf);
        wfh.setEndDateHalfDayType(endHalf);
        wfh.setTotalDays(totalDays);
        wfh.setReason(request.getReason());
        wfh.setUpdatedBy(request.getEmployeeId());

        if (request.getAttachment() != null && !request.getAttachment().isEmpty()) {
            saveAttachment(request.getAttachment(), wfh);
        }

        return WfhMapper.toDTO(wfhRepository.save(wfh));
    }

    // ═══════════════════════════════════════════════════════════════
    // GET MY WFH APPLICATIONS
    // ═══════════════════════════════════════════════════════════════

    public List<WfhResponseDTO> getMyWfhApplications(String empId) {
        return wfhRepository
                .findByEmployee_EmpIdOrderByCreatedAtDesc(empId)
                .stream()
                .map(WfhMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET TOTAL WFH DAYS
    // ═══════════════════════════════════════════════════════════════

    public BigDecimal getTotalWfhDays(String empId) {
        BigDecimal total = wfhRepository.sumTotalDaysByEmployee(empId);
        return total != null ? total : BigDecimal.ZERO;
    }

    // ═══════════════════════════════════════════════════════════════
    // CANCEL
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public WfhResponseDTO cancelWfh(Long wfhId, String empId) {
        WfhApplication wfh = wfhRepository.findById(wfhId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "WFH application not found: " + wfhId));

        if (!wfh.getEmployeeId().equals(empId)) {
            throw new BadRequestException(
                    "You are not authorized to cancel this WFH application.");
        }
        if (wfh.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING WFH applications can be cancelled. Current status: " + wfh.getStatus());
        }

        wfh.setStatus(RequestStatus.CANCELLED);
        wfh.setCurrentApproverId(null);
        wfh.setCurrentApprovalLevel(null);
        wfh.setUpdatedBy(empId);

        return WfhMapper.toDTO(wfhRepository.save(wfh));
    }

    // ═══════════════════════════════════════════════════════════════
    // GET ATTACHMENT
    // ═══════════════════════════════════════════════════════════════

    public ResponseEntity<Resource> getAttachment(Long wfhId) {

        WfhApplication wfh = wfhRepository.findById(wfhId)
                .orElseThrow(() -> new EntityNotFoundException("WFH not found: " + wfhId));

        String storedFileName = wfh.getAttachmentFileName();
        if (storedFileName == null || storedFileName.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Uses the same UPLOAD_DIR constant as saveAttachment()
            Path filePath = Paths.get(UPLOAD_DIR).resolve(storedFileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = wfh.getAttachmentContentType();
            MediaType mediaType;
            try {
                mediaType = (contentType != null)
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String originalName = wfh.getAttachmentOriginalName() != null
                    ? wfh.getAttachmentOriginalName()
                    : storedFileName;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + originalName + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — OVERLAP CHECKS
    // ═══════════════════════════════════════════════════════════════

    private void checkLeaveOverlap(String empId, LocalDate startDate, LocalDate endDate) {
        var overlapping = leaveRepository.findOverlappingLeaves(empId, startDate, endDate);
        if (!overlapping.isEmpty()) {
            var existing = overlapping.get(0);
            throw new BadRequestException(
                    "You already have a " + existing.getStatus().name().toLowerCase()
                            + " leave from " + existing.getStartDate()
                            + " to " + existing.getEndDate()
                            + ". Please choose different dates.");
        }
    }

    private void checkWfhOverlap(String empId, LocalDate startDate, LocalDate endDate, Long excludeId) {
        List<WfhApplication> overlapping = excludeId != null
                ? wfhRepository.findOverlappingWfhExcluding(empId, startDate, endDate, excludeId)
                : wfhRepository.findOverlappingWfh(empId, startDate, endDate);

        if (!overlapping.isEmpty()) {
            var existing = overlapping.get(0);
            throw new BadRequestException(
                    "You already have a " + existing.getStatus().name().toLowerCase()
                            + " WFH request from " + existing.getStartDate()
                            + " to " + existing.getEndDate()
                            + ". Please choose different dates.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — CALCULATE DAYS
    // ═══════════════════════════════════════════════════════════════

    public BigDecimal calculateDays(LocalDate start, LocalDate end,
                                    HalfDayType startHalf, HalfDayType endHalf) {
        if (start.equals(end)) {
            if (startHalf != null && endHalf != null) return BigDecimal.ONE;
            if (startHalf != null || endHalf != null) return new BigDecimal("0.5");
            return BigDecimal.ONE;
        }
        long totalCalendarDays = ChronoUnit.DAYS.between(start, end) + 1;
        BigDecimal days = new BigDecimal(totalCalendarDays);
        if (startHalf != null) days = days.subtract(new BigDecimal("0.5"));
        if (endHalf   != null) days = days.subtract(new BigDecimal("0.5"));
        return days.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : days;
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — VALIDATE DATES
    // ═══════════════════════════════════════════════════════════════

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start date and end date are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("End date cannot be before start date.");
        }
        LocalDate minAllowed = LocalDate.now().minusDays(MAX_BACKDATE_DAYS);
        if (startDate.isBefore(minAllowed)) {
            throw new BadRequestException(
                    "WFH can only be applied within 31 days in the past. Earliest allowed: " + minAllowed);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — PARSE HALF-DAY
    // ═══════════════════════════════════════════════════════════════

    private HalfDayType parseHalfDay(String value) {
        if (value == null || value.isBlank()) return null;
        try { return HalfDayType.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — SAVE ATTACHMENT
    // ═══════════════════════════════════════════════════════════════

    private void saveAttachment(MultipartFile file, WfhApplication wfh) {
        if (file == null || file.isEmpty()) return;
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
            String originalName = StringUtils.cleanPath(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment");
            String ext = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) ext = originalName.substring(dot);
            String storedName = UUID.randomUUID() + ext;
            Path filePath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            wfh.setAttachmentFileName(storedName);
            wfh.setAttachmentOriginalName(originalName);
            wfh.setAttachmentContentType(file.getContentType());
            wfh.setAttachmentPath(filePath.toAbsolutePath().toString());
            wfh.setAttachmentSize(file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store WFH attachment: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — APPROVAL CHAIN SETUP
    // ═══════════════════════════════════════════════════════════════

    private void setupApprovalChain(WfhApplication wfh, Employee employee) {
        String firstApproverEmpId = employee.getReportingId();
        if (firstApproverEmpId == null) {
            wfh.setFirstApproverId(null);
            wfh.setSecondApproverId(null);
            wfh.setCurrentApproverId(null);
            wfh.setCurrentApprovalLevel(null);
            wfh.setRequiredApprovalLevels(0);
            return;
        }
        Employee firstApprover = employeeRepository
                .findByEmpId(firstApproverEmpId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "First approver not found: " + firstApproverEmpId));
        wfh.setFirstApproverId(firstApprover.getEmpId());
        wfh.setCurrentApproverId(firstApprover.getEmpId());
        wfh.setCurrentApprovalLevel(ApprovalLevel.FIRST_APPROVER);
        String secondApproverEmpId = firstApprover.getReportingId();
        if (secondApproverEmpId == null) {
            wfh.setSecondApproverId(null);
            wfh.setRequiredApprovalLevels(1);
        } else {
            Employee secondApprover = employeeRepository
                    .findByEmpId(secondApproverEmpId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Second approver not found: " + secondApproverEmpId));
            wfh.setSecondApproverId(secondApprover.getEmpId());
            wfh.setRequiredApprovalLevels(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════

    private void notifyFirstApprover(WfhApplication wfh, Employee employee) {
        if (wfh.getFirstApproverId() == null) return;
        employeeRepository.findByEmpId(wfh.getFirstApproverId()).ifPresent(approver -> {
            String subject = "New WFH Request from " + employee.getName();
            String body = String.format(
                    "%s has applied for Work From Home from %s to %s (%s days). Awaiting your approval.",
                    employee.getName(), wfh.getStartDate(), wfh.getEndDate(), wfh.getTotalDays());
            notificationService.createNotification(
                    approver.getEmpId(), employee.getEmail(), approver.getEmail(),
                    EventType.LEAVE_APPLIED, Channel.EMAIL, subject, body);
        });
    }

    private void notifyAdmin(WfhApplication wfh, String senderEmail, String status) {
        employeeRepository.findAllByRoleName("ADMIN")
                .stream().findFirst().ifPresent(admin -> {
                    String subject = "WFH Request — " + status;
                    String body = String.format(
                            "%s applied for WFH from %s to %s (%s days). Status: %s.",
                            wfh.getEmployeeName(), wfh.getStartDate(), wfh.getEndDate(),
                            wfh.getTotalDays(), status);
                    notificationService.createNotification(
                            admin.getEmpId(), senderEmail, admin.getEmail(),
                            EventType.LEAVE_APPLIED, Channel.EMAIL, subject, body);
                });
    }
}