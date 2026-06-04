package com.emp_management.feature.permission.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.notification.service.NotificationService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionApprovalService {

    private final PermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public PermissionApprovalService(
            PermissionRepository permissionRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService) {
        this.permissionRepository = permissionRepository;
        this.employeeRepository   = employeeRepository;
        this.notificationService  = notificationService;
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTION CENTER — PENDING LIST FOR APPROVER
    // ═══════════════════════════════════════════════════════════════

    public List<PermissionResponseDTO> getPendingForApprover(String approverId) {
        return permissionRepository
                .findByCurrentApproverIdAndStatus(approverId, RequestStatus.PENDING)
                .stream()
                .map(PermissionMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // APPROVE
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO approvePermission(Long permissionId,
                                                   String approverId,
                                                   String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new BadRequestException("Remark is required when approving.");
        }

        Permission permission = getOrThrow(permissionId);
        validatePending(permission);

        Employee approver = getApproverOrThrow(approverId);
        validateApproverForLevel(permission, approver);

        ApprovalLevel currentLevel = permission.getCurrentApprovalLevel();
        recordDecision(permission, currentLevel, RequestStatus.APPROVED);
        permission.setUpdatedBy(approverId);

        // Level 1 approved + 2 levels required → advance to level 2
        if (currentLevel == ApprovalLevel.FIRST_APPROVER
                && permission.getRequiredApprovalLevels() >= 2) {

            permission.setCurrentApprovalLevel(ApprovalLevel.SECOND_APPROVER);
            permission.setCurrentApproverId(permission.getSecondApproverId());
            permissionRepository.save(permission);

            notifySecondApprover(permission, approver);
            notifyEmployeeProgress(permission, approver,
                    "Your permission request on "
                            + permission.getPermissionDate()
                            + " has been approved at level 1. Pending final approval.");

        } else {
            finalizePermission(permission, RequestStatus.APPROVED, approver, comments);
        }

        return PermissionMapper.toDTO(permission);
    }

    // ═══════════════════════════════════════════════════════════════
    // REJECT
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public PermissionResponseDTO rejectPermission(Long permissionId,
                                                  String approverId,
                                                  String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new BadRequestException("Remark is required when rejecting.");
        }

        Permission permission = getOrThrow(permissionId);
        validatePending(permission);

        Employee approver = getApproverOrThrow(approverId);
        validateApproverForLevel(permission, approver);

        recordDecision(permission,
                permission.getCurrentApprovalLevel(),
                RequestStatus.REJECTED);

        permission.setUpdatedBy(approverId);
        finalizePermission(permission, RequestStatus.REJECTED, approver, comments);

        return PermissionMapper.toDTO(permission);
    }

    // ═══════════════════════════════════════════════════════════════
    // FINALIZE
    // ═══════════════════════════════════════════════════════════════

    private void finalizePermission(Permission permission,
                                    RequestStatus finalStatus,
                                    Employee approver,
                                    String comments) {
        permission.setStatus(finalStatus);
        permission.setApprovedBy(approver.getEmpId());
        permission.setApprovedRole(approver.getRole().getRoleName());
        permission.setApprovedAt(LocalDateTime.now());

        // ↓ These two lines remove it from Action Center after decision
        permission.setCurrentApproverId(null);
        permission.setCurrentApprovalLevel(null);

        if (finalStatus == RequestStatus.REJECTED) {
            permission.setRejectionReason(comments);
        }

        permissionRepository.save(permission);
        notifyEmployee(permission, approver, finalStatus, comments);
        notifyAdmin(permission, approver, finalStatus);
    }

    // ═══════════════════════════════════════════════════════════════
    // RECORD LEVEL DECISION
    // ═══════════════════════════════════════════════════════════════

    private void recordDecision(Permission permission,
                                ApprovalLevel level,
                                RequestStatus decision) {
        switch (level) {
            case FIRST_APPROVER -> {
                permission.setFirstApproverDecision(decision);
                permission.setFirstApproverDecidedAt(LocalDateTime.now());
            }
            case SECOND_APPROVER -> {
                permission.setSecondApproverDecision(decision);
                permission.setSecondApproverDecidedAt(LocalDateTime.now());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATE APPROVER
    // ═══════════════════════════════════════════════════════════════

    private void validateApproverForLevel(Permission permission,
                                          Employee approver) {
        switch (permission.getCurrentApprovalLevel()) {
            case FIRST_APPROVER -> {
                if (!approver.getEmpId().equals(permission.getFirstApproverId())) {
                    throw new BadRequestException(
                            "Unauthorized: You are not the level-1 approver.");
                }
            }
            case SECOND_APPROVER -> {
                if (!approver.getEmpId().equals(permission.getSecondApproverId())) {
                    throw new BadRequestException(
                            "Unauthorized: You are not the level-2 approver.");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════

    private void notifySecondApprover(Permission permission,
                                      Employee firstApprover) {
        if (permission.getSecondApproverId() == null) return;

        employeeRepository
                .findByEmpId(permission.getSecondApproverId())
                .ifPresent(mgr -> {
                    String subject = "Permission Request — " + permission.getEmployeeName();
                    String body = String.format(
                            "%s's permission on %s (%s to %s) has been approved "
                                    + "at level 1 by %s. Awaiting your approval.",
                            permission.getEmployeeName(),
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            firstApprover.getName());

                    // EMAIL notification
                    notificationService.createNotification(
                            mgr.getEmpId(),
                            firstApprover.getEmail(),
                            mgr.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.EMAIL,
                            subject,
                            body);
                    // IN_APP notification (bell icon)
                    notificationService.createNotification(
                            mgr.getEmpId(),
                            firstApprover.getEmail(),
                            mgr.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.IN_APP,
                            subject,
                            body);
                });
    }

    private void notifyEmployee(Permission permission,
                                Employee approver,
                                RequestStatus status,
                                String comments) {
        employeeRepository
                .findByEmpId(permission.getEmployeeId())
                .ifPresent(emp -> {
                    String subject = "Permission " + status.name()
                            + " — " + permission.getPermissionDate();

                    String body = status == RequestStatus.APPROVED
                            ? String.format(
                            "Your permission on %s from %s to %s has been approved by %s.",
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            approver.getName())
                            : String.format(
                            "Your permission on %s from %s to %s has been rejected by %s. Reason: %s",
                            permission.getPermissionDate(),
                            permission.getStartTime(),
                            permission.getEndTime(),
                            approver.getName(),
                            comments);

                    EventType permEvt = status == RequestStatus.APPROVED
                            ? EventType.PERMISSION_APPROVED
                            : EventType.PERMISSION_REJECTED;
                    // EMAIL notification
                    notificationService.createNotification(
                            emp.getEmpId(),
                            approver.getEmail(),
                            emp.getEmail(),
                            permEvt,
                            Channel.EMAIL,
                            subject,
                            body);
                    // IN_APP notification (bell icon)
                    notificationService.createNotification(
                            emp.getEmpId(),
                            approver.getEmail(),
                            emp.getEmail(),
                            permEvt,
                            Channel.IN_APP,
                            subject,
                            body);
                });
    }

    private void notifyEmployeeProgress(Permission permission,
                                        Employee approver,
                                        String message) {
        employeeRepository
                .findByEmpId(permission.getEmployeeId())
                .ifPresent(emp -> {
                    String subject = "Permission Update — " + permission.getPermissionDate();

                    // EMAIL notification
                    notificationService.createNotification(
                            emp.getEmpId(),
                            approver.getEmail(),
                            emp.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.EMAIL,
                            subject,
                            message);
                    // IN_APP notification (bell icon)
                    notificationService.createNotification(
                            emp.getEmpId(),
                            approver.getEmail(),
                            emp.getEmail(),
                            EventType.PERMISSION_APPLIED,
                            Channel.IN_APP,
                            subject,
                            message);
                });
    }

    private void notifyAdmin(Permission permission,
                             Employee approver,
                             RequestStatus status) {
        employeeRepository.findAllByRoleName("ADMIN")
                .stream()
                .findFirst()
                .ifPresent(admin -> {
                    String subject = "Permission " + status.name()
                            + " — " + permission.getEmployeeName();
                    String body = String.format(
                            "Permission for %s on %s has been %s by %s.",
                            permission.getEmployeeName(),
                            permission.getPermissionDate(),
                            status.name().toLowerCase(),
                            approver.getName());

                    notificationService.createNotification(
                            admin.getEmpId(),
                            approver.getEmail(),
                            admin.getEmail(),
                            status == RequestStatus.APPROVED
                                    ? EventType.PERMISSION_APPROVED
                                    : EventType.PERMISSION_REJECTED,
                            Channel.EMAIL,
                            subject,
                            body);
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private Permission getOrThrow(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission not found: " + id));
    }

    private Employee getApproverOrThrow(String approverId) {
        return employeeRepository.findByEmpId(approverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Approver not found: " + approverId));
    }

    private void validatePending(Permission permission) {
        if (permission.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Permission already processed: " + permission.getStatus());
        }
    }
}