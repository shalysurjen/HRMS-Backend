package com.emp_management.feature.permission.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.notification.service.NotificationService;
import com.emp_management.feature.permission.dto.PermissionRequestDTO;
import com.emp_management.feature.permission.dto.PermissionResponseDTO;
import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.feature.permission.mapper.PermissionMapper;
import com.emp_management.feature.permission.repository.PermissionRepository;
import com.emp_management.shared.enums.ApprovalLevel;
import com.emp_management.shared.enums.Channel;
import com.emp_management.shared.enums.EventType;
import com.emp_management.shared.enums.RequestStatus;
import com.emp_management.shared.exceptions.BadRequestException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
public class PermissionService {

    private static final LocalTime OFFICE_START      = LocalTime.of(9, 15);
    private static final LocalTime OFFICE_END        = LocalTime.of(18, 30);
    private static final long      MAX_BACKDATE_DAYS = 31;

    // ── NEW: folder where attachments are stored on disk ──────────
    // Change this path to wherever your server stores uploads
    private static final String UPLOAD_DIR = "uploads/permissions/";

    private final PermissionRepository permissionRepository;
    private final EmployeeRepository   employeeRepository;
    private final NotificationService  notificationService;

    public PermissionService(
            PermissionRepository permissionRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService) {
        this.permissionRepository = permissionRepository;
        this.employeeRepository   = employeeRepository;
        this.notificationService  = notificationService;
    }

    // ═══════════════════════════════════════════════════════════════
    // APPLY PERMISSION
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO applyPermission(PermissionRequestDTO request) {

        Employee employee = employeeRepository
                .findByEmpId(request.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee not found: " + request.getEmployeeId()));

        validatePermissionDate(request.getPermissionDate());
        validateTimeRange(request.getStartTime(), request.getEndTime());

        int durationMinutes = (int) ChronoUnit.MINUTES.between(
                request.getStartTime(), request.getEndTime());

        Permission permission = new Permission();
        permission.setEmployee(employee);
        permission.setPermissionDate(request.getPermissionDate());
        permission.setStartTime(request.getStartTime());
        permission.setEndTime(request.getEndTime());
        permission.setDurationMinutes(durationMinutes);
        permission.setReason(request.getReason());
        permission.setStatus(RequestStatus.PENDING);
        permission.setCreatedBy(employee.getEmpId());
        permission.setUpdatedBy(employee.getEmpId());

        // ── NEW: handle file attachment ────────────────────────────
        saveAttachment(request.getAttachment(), permission);

        setupApprovalChain(permission, employee);

        if (permission.getRequiredApprovalLevels() == 0) {
            permission.setStatus(RequestStatus.APPROVED);
            permission.setApprovedBy(employee.getEmpId());
            permission.setApprovedRole(employee.getRole().getRoleName());
            permission.setApprovedAt(LocalDateTime.now());
            Permission saved = permissionRepository.save(permission);
            notifyAdmin(saved, employee.getEmail(), "Auto-Approved");
            return PermissionMapper.toDTO(saved);
        }

        Permission saved = permissionRepository.save(permission);
        notifyFirstApprover(saved, employee);
        notifyAdmin(saved, employee.getEmail(), "Pending Approval");
        return PermissionMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════
    // GET EMPLOYEE'S OWN PERMISSIONS
    // ═══════════════════════════════════════════════════════════════

    public List<PermissionResponseDTO> getMyPermissions(String empId) {
        return permissionRepository
                .findByEmployee_EmpIdOrderByCreatedAtDesc(empId)
                .stream()
                .map(PermissionMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET PENDING FOR APPROVER
    // ═══════════════════════════════════════════════════════════════

    public List<PermissionResponseDTO> getPendingForApprover(String approverId) {
        return permissionRepository
                .findByCurrentApproverIdAndStatus(approverId, RequestStatus.PENDING)
                .stream()
                .map(PermissionMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // APPROVE PERMISSION
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO approvePermission(Long permissionId,
                                                   String approverId,
                                                   String comments) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + permissionId));

        if (permission.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING permissions can be approved. Current status: "
                            + permission.getStatus());
        }
        if (!approverId.equals(permission.getCurrentApproverId())) {
            throw new BadRequestException(
                    "You are not the current approver for this permission.");
        }

        Employee approver = employeeRepository.findByEmpId(approverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Approver not found: " + approverId));

        // First-level approval: check if second approver exists
        if (ApprovalLevel.FIRST_APPROVER.equals(permission.getCurrentApprovalLevel())
                && permission.getSecondApproverId() != null) {
            // Escalate to second approver
            permission.setCurrentApproverId(permission.getSecondApproverId());
            permission.setCurrentApprovalLevel(ApprovalLevel.SECOND_APPROVER);
        } else {
            // Final approval
            permission.setStatus(RequestStatus.APPROVED);
            permission.setApprovedBy(approverId);
            permission.setApprovedRole(approver.getRole().getRoleName());
            permission.setApprovedAt(LocalDateTime.now());
            permission.setCurrentApproverId(null);
            permission.setCurrentApprovalLevel(null);

            // Notify the employee
            employeeRepository.findByEmpId(permission.getEmployeeId())
                    .ifPresent(emp -> {
                        String subject = "Your Permission Request has been Approved";
                        String body = String.format(
                                "Your permission on %s (%s to %s) has been approved by %s.",
                                permission.getPermissionDate(),
                                permission.getStartTime(),
                                permission.getEndTime(),
                                approver.getName());
                        notificationService.createNotification(
                                emp.getEmpId(),
                                approver.getEmail(),
                                emp.getEmail(),
                                EventType.PERMISSION_APPLIED,
                                Channel.EMAIL,
                                subject,
                                body);
                    });
        }

        Permission saved = permissionRepository.save(permission);
        return PermissionMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════
    // REJECT PERMISSION
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO rejectPermission(Long permissionId,
                                                  String approverId,
                                                  String comments) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + permissionId));

        if (permission.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING permissions can be rejected. Current status: "
                            + permission.getStatus());
        }
        if (!approverId.equals(permission.getCurrentApproverId())) {
            throw new BadRequestException(
                    "You are not the current approver for this permission.");
        }

        Employee approver = employeeRepository.findByEmpId(approverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Approver not found: " + approverId));

        permission.setStatus(RequestStatus.REJECTED);
        permission.setApprovedBy(approverId);          // reusing approvedBy for rejector
        permission.setApprovedRole(approver.getRole().getRoleName());
        permission.setApprovedAt(LocalDateTime.now());
        permission.setCurrentApproverId(null);
        permission.setCurrentApprovalLevel(null);

        // Notify the employee
        employeeRepository.findByEmpId(permission.getEmployeeId())
                .ifPresent(emp -> {
                    String subject = "Your Permission Request has been Rejected";
                    String body = String.format(
                            "Your permission on %s (%s to %s) has been rejected by %s. Reason: %s",
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            approver.getName(),
                            comments != null ? comments : "No reason provided");
                    notificationService.createNotification(
                            emp.getEmpId(),
                            approver.getEmail(),
                            emp.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.EMAIL,
                            subject,
                            body);
                });

        Permission saved = permissionRepository.save(permission);
        return PermissionMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════
    // EDIT PERMISSION (unchanged — edit doesn't update attachment)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO editPermission(Long permissionId,
                                                String empId,
                                                PermissionRequestDTO request) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + permissionId));

        if (!permission.getEmployeeId().equals(empId)) {
            throw new BadRequestException(
                    "You are not authorized to edit this permission.");
        }
        if (permission.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING permissions can be edited. Current status: "
                            + permission.getStatus());
        }

        validateTimeRange(request.getStartTime(), request.getEndTime());

        int durationMinutes = (int) ChronoUnit.MINUTES.between(
                request.getStartTime(), request.getEndTime());

        permission.setStartTime(request.getStartTime());
        permission.setEndTime(request.getEndTime());
        permission.setDurationMinutes(durationMinutes);
        if (request.getReason() != null && !request.getReason().isBlank()) {
            permission.setReason(request.getReason());
        }
        permission.setUpdatedBy(empId);

        Permission saved = permissionRepository.save(permission);
        return PermissionMapper.toDTO(saved);
    }
    public ResponseEntity<Resource> getAttachment(Long permissionId) {

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + permissionId));

        String storedFileName = permission.getAttachmentFileName();
        if (storedFileName == null || storedFileName.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(storedFileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = permission.getAttachmentContentType();
            MediaType mediaType;
            try {
                mediaType = (contentType != null)
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String originalName = permission.getAttachmentOriginalName() != null
                    ? permission.getAttachmentOriginalName()
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
    // CANCEL PERMISSION (unchanged)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO cancelPermission(Long permissionId, String empId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + permissionId));

        if (!permission.getEmployeeId().equals(empId)) {
            throw new BadRequestException(
                    "You are not authorized to cancel this permission.");
        }
        if (permission.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING permissions can be cancelled. Current status: "
                            + permission.getStatus());
        }

        permission.setStatus(RequestStatus.CANCELLED);
        permission.setCurrentApproverId(null);
        permission.setCurrentApprovalLevel(null);
        permission.setUpdatedBy(empId);

        Permission saved = permissionRepository.save(permission);
        return PermissionMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════
    // NEW: SAVE ATTACHMENT — writes file to disk, sets entity fields
    // ═══════════════════════════════════════════════════════════════

    private void saveAttachment(MultipartFile file, Permission permission) {
        // No file uploaded — skip silently, all attachment fields stay null
        if (file == null || file.isEmpty()) return;

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique stored filename to prevent collisions
            String originalName = StringUtils.cleanPath(
                    file.getOriginalFilename() != null
                            ? file.getOriginalFilename()
                            : "attachment");
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex); // includes the dot
            }
            String storedName = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(storedName);

            // Write bytes to disk
            Files.copy(file.getInputStream(), filePath,
                    StandardCopyOption.REPLACE_EXISTING);

            // Populate the 5 entity fields
            permission.setAttachmentFileName(storedName);
            permission.setAttachmentOriginalName(originalName);
            permission.setAttachmentContentType(file.getContentType());
            permission.setAttachmentPath(filePath.toAbsolutePath().toString());
            permission.setAttachmentSize(file.getSize());

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to store permission attachment: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATE DATE
    // ═══════════════════════════════════════════════════════════════

    private void validatePermissionDate(LocalDate permissionDate) {
        LocalDate today      = LocalDate.now();
        LocalDate minAllowed = today.minusDays(MAX_BACKDATE_DAYS);

        // Future dates allowed — no upper bound restriction
        if (permissionDate.isBefore(minAllowed)) {
            throw new BadRequestException(
                    "Permission can only be applied within 31 days. "
                            + "Earliest allowed date: " + minAllowed);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATE TIME
    // ═══════════════════════════════════════════════════════════════

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException(
                    "Start time and end time are required.");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException(
                    "End time must be after start time.");
        }
        if (startTime.isBefore(OFFICE_START)) {
            throw new BadRequestException(
                    "Start time cannot be before 09:15 AM.");
        }
        if (endTime.isAfter(OFFICE_END)) {
            throw new BadRequestException(
                    "End time cannot be after 06:30 PM.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // APPROVAL CHAIN SETUP (unchanged)
    // ═══════════════════════════════════════════════════════════════

    private void setupApprovalChain(Permission permission, Employee employee) {
        String firstApproverEmpId = employee.getReportingId();

        if (firstApproverEmpId == null) {
            permission.setFirstApproverId(null);
            permission.setSecondApproverId(null);
            permission.setCurrentApproverId(null);
            permission.setCurrentApprovalLevel(null);
            permission.setRequiredApprovalLevels(0);
            return;
        }

        Employee firstApprover = employeeRepository
                .findByEmpId(firstApproverEmpId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "First approver not found: " + firstApproverEmpId));

        permission.setFirstApproverId(firstApprover.getEmpId());
        permission.setCurrentApproverId(firstApprover.getEmpId());
        permission.setCurrentApprovalLevel(ApprovalLevel.FIRST_APPROVER);

        String secondApproverEmpId = firstApprover.getReportingId();

        if (secondApproverEmpId == null) {
            permission.setSecondApproverId(null);
            permission.setRequiredApprovalLevels(1);
        } else {
            Employee secondApprover = employeeRepository
                    .findByEmpId(secondApproverEmpId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Second approver not found: " + secondApproverEmpId));
            permission.setSecondApproverId(secondApprover.getEmpId());
            permission.setRequiredApprovalLevels(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFY FIRST APPROVER (unchanged)
    // ═══════════════════════════════════════════════════════════════

    private void notifyFirstApprover(Permission permission, Employee employee) {
        if (permission.getFirstApproverId() == null) return;

        employeeRepository
                .findByEmpId(permission.getFirstApproverId())
                .ifPresent(approver -> {
                    String subject = "New Permission Request from "
                            + employee.getName();
                    String body = String.format(
                            "%s has applied for permission on %s from %s to %s (%s). "
                                    + "Awaiting your approval.",
                            employee.getName(),
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            formatDuration(permission.getDurationMinutes()));

                    notificationService.createNotification(
                            approver.getEmpId(),
                            employee.getEmail(),
                            approver.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.EMAIL,
                            subject,
                            body);
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFY ADMIN (unchanged)
    // ═══════════════════════════════════════════════════════════════

    private void notifyAdmin(Permission permission,
                             String senderEmail,
                             String status) {
        employeeRepository.findAllByRoleName("ADMIN")
                .stream()
                .findFirst()
                .ifPresent(admin -> {
                    String subject = "Permission Request — " + status;
                    String body = String.format(
                            "%s applied for permission on %s (%s to %s). Status: %s.",
                            permission.getEmployeeName(),
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            status);

                    notificationService.createNotification(
                            admin.getEmpId(),
                            senderEmail,
                            admin.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.EMAIL,
                            subject,
                            body);
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // FORMAT DURATION (unchanged)
    // ═══════════════════════════════════════════════════════════════

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return "0 mins";
        int hrs  = minutes / 60;
        int mins = minutes % 60;
        if (hrs > 0 && mins > 0)
            return hrs + " hr" + (hrs > 1 ? "s" : "")
                    + " " + mins + " min" + (mins > 1 ? "s" : "");
        if (hrs > 0) return hrs + " hr" + (hrs > 1 ? "s" : "");
        return mins + " min" + (mins > 1 ? "s" : "");
    }
}