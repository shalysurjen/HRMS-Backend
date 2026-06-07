package com.emp_management.feature.wfh.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.notification.service.NotificationService;
import com.emp_management.feature.wfh.dto.WfhResponseDTO;
import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.feature.wfh.mapper.WfhMapper;
import com.emp_management.feature.wfh.repository.WfhApplicationRepository;
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
public class WfhApprovalService {

    private final WfhApplicationRepository wfhRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public WfhApprovalService(WfhApplicationRepository wfhRepository,
                              EmployeeRepository employeeRepository,
                              NotificationService notificationService) {
        this.wfhRepository     = wfhRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    // ═══════════════════════════════════════════════════════════════
    // PENDING LIST FOR APPROVER
    // ═══════════════════════════════════════════════════════════════

    public List<WfhResponseDTO> getPendingForApprover(String approverId) {
        return wfhRepository
                .findByCurrentApproverIdAndStatus(approverId, RequestStatus.PENDING)
                .stream()
                .map(WfhMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // APPROVE
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public WfhResponseDTO approveWfh(Long wfhId, String approverId, String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new BadRequestException("Remark is required when approving.");
        }

        WfhApplication wfh = getOrThrow(wfhId);
        validatePending(wfh);
        Employee approver = getApproverOrThrow(approverId);
        validateApproverForLevel(wfh, approver);

        ApprovalLevel currentLevel = wfh.getCurrentApprovalLevel();
        recordDecision(wfh, currentLevel, RequestStatus.APPROVED);
        wfh.setUpdatedBy(approverId);

        // Level-1 approved and 2 levels required → advance to level-2
        if (currentLevel == ApprovalLevel.FIRST_APPROVER
                && wfh.getRequiredApprovalLevels() >= 2) {

            wfh.setCurrentApprovalLevel(ApprovalLevel.SECOND_APPROVER);
            wfh.setCurrentApproverId(wfh.getSecondApproverId());
            wfhRepository.save(wfh);

            notifySecondApprover(wfh, approver);
            notifyEmployeeProgress(wfh, "Your WFH request from " + wfh.getStartDate()
                    + " to " + wfh.getEndDate()
                    + " has been approved at level 1. Pending final approval.");
        } else {
            finalizeWfh(wfh, RequestStatus.APPROVED, approver, comments);
        }

        return WfhMapper.toDTO(wfh);
    }

    // ═══════════════════════════════════════════════════════════════
    // REJECT
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public WfhResponseDTO rejectWfh(Long wfhId, String approverId, String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new BadRequestException("Remark is required when rejecting.");
        }

        WfhApplication wfh = getOrThrow(wfhId);
        validatePending(wfh);
        Employee approver = getApproverOrThrow(approverId);
        validateApproverForLevel(wfh, approver);

        recordDecision(wfh, wfh.getCurrentApprovalLevel(), RequestStatus.REJECTED);
        wfh.setUpdatedBy(approverId);
        finalizeWfh(wfh, RequestStatus.REJECTED, approver, comments);

        return WfhMapper.toDTO(wfh);
    }

    // ═══════════════════════════════════════════════════════════════
    // FINALIZE
    // ═══════════════════════════════════════════════════════════════

    private void finalizeWfh(WfhApplication wfh, RequestStatus status,
                             Employee approver, String comments) {
        wfh.setStatus(status);
        wfh.setCurrentApproverId(null);
        wfh.setCurrentApprovalLevel(null);
        wfh.setApprovedBy(approver.getEmpId());
        wfh.setApprovedRole(approver.getRole().getRoleName());
        wfh.setApprovedAt(LocalDateTime.now());

        if (status == RequestStatus.REJECTED) {
            wfh.setRejectionReason(comments);
        }

        wfhRepository.save(wfh);
        notifyEmployee(wfh, approver, status, comments);
    }

    // ═══════════════════════════════════════════════════════════════
    // RECORD DECISION ON ENTITY
    // ═══════════════════════════════════════════════════════════════

    private void recordDecision(WfhApplication wfh, ApprovalLevel level, RequestStatus decision) {
        if (level == ApprovalLevel.FIRST_APPROVER) {
            wfh.setFirstApproverDecision(decision);
            wfh.setFirstApproverDecidedAt(LocalDateTime.now());
        } else {
            wfh.setSecondApproverDecision(decision);
            wfh.setSecondApproverDecidedAt(LocalDateTime.now());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATIONS
    // ═══════════════════════════════════════════════════════════════

    private void validatePending(WfhApplication wfh) {
        if (wfh.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "WFH application is already processed with status: " + wfh.getStatus());
        }
    }

    private void validateApproverForLevel(WfhApplication wfh, Employee approver) {
        ApprovalLevel level = wfh.getCurrentApprovalLevel();
        String expected = (level == ApprovalLevel.FIRST_APPROVER)
                ? wfh.getFirstApproverId() : wfh.getSecondApproverId();
        if (!approver.getEmpId().equals(expected)) {
            throw new BadRequestException(
                    "You are not the current approver for this WFH application.");
        }
    }

    private WfhApplication getOrThrow(Long id) {
        return wfhRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "WFH application not found: " + id));
    }

    private Employee getApproverOrThrow(String approverId) {
        return employeeRepository.findByEmpId(approverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Approver not found: " + approverId));
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════

    private void notifySecondApprover(WfhApplication wfh, Employee firstApprover) {
        if (wfh.getSecondApproverId() == null) return;
        employeeRepository.findByEmpId(wfh.getSecondApproverId()).ifPresent(secondApprover -> {
            String subject = "WFH Request Forwarded for Final Approval";
            String body = String.format(
                    "%s's WFH from %s to %s has been approved at level 1 by %s. Awaiting your final approval.",
                    wfh.getEmployeeName(), wfh.getStartDate(), wfh.getEndDate(),
                    firstApprover.getName());
            // EMAIL notification
            notificationService.createNotification(
                    secondApprover.getEmpId(), firstApprover.getEmail(), secondApprover.getEmail(),
                    EventType.LEAVE_APPLIED, Channel.EMAIL, subject, body);
            // IN_APP notification (bell icon)
            notificationService.createNotification(
                    secondApprover.getEmpId(), firstApprover.getEmail(), secondApprover.getEmail(),
                    EventType.LEAVE_APPLIED, Channel.IN_APP, subject, body);
        });
    }

    private void notifyEmployee(WfhApplication wfh, Employee approver,
                                RequestStatus status, String comments) {
        employeeRepository.findByEmpId(wfh.getEmployeeId()).ifPresent(employee -> {
            String verb    = status == RequestStatus.APPROVED ? "Approved" : "Rejected";
            String subject = "Your WFH Request has been " + verb;
            String body    = String.format(
                    "Your WFH from %s to %s has been %s by %s.%s",
                    wfh.getStartDate(), wfh.getEndDate(), verb.toLowerCase(),
                    approver.getName(),
                    status == RequestStatus.REJECTED ? " Reason: " + comments : "");
            EventType wfhEvt = status == RequestStatus.APPROVED ? EventType.LEAVE_APPROVED : EventType.LEAVE_REJECTED;
            // EMAIL notification
            notificationService.createNotification(
                    employee.getEmpId(), approver.getEmail(), employee.getEmail(),
                    wfhEvt, Channel.EMAIL, subject, body);
            // IN_APP notification (bell icon)
            notificationService.createNotification(
                    employee.getEmpId(), approver.getEmail(), employee.getEmail(),
                    wfhEvt, Channel.IN_APP, subject, body);
        });
    }

    private void notifyEmployeeProgress(WfhApplication wfh, String message) {
        employeeRepository.findByEmpId(wfh.getEmployeeId()).ifPresent(employee -> {
            // EMAIL notification
            notificationService.createNotification(
                    employee.getEmpId(), null, employee.getEmail(),
                    EventType.LEAVE_IN_PROGRESS, Channel.EMAIL,
                    "WFH Request Update", message);
            // IN_APP notification (bell icon)
            notificationService.createNotification(
                    employee.getEmpId(), null, employee.getEmail(),
                    EventType.LEAVE_IN_PROGRESS, Channel.IN_APP,
                    "WFH Request Update", message);
        });
    }
}
