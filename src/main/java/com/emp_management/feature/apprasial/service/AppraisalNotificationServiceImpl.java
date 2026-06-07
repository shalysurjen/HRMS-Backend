package com.emp_management.feature.apprasial.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.notification.service.NotificationService;
import com.emp_management.shared.enums.Channel;
import com.emp_management.shared.enums.EventType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Real implementation of AppraisalNotificationService.
 *
 * Fix: Previously send() created TWO createNotification records (IN_APP + EMAIL)
 * which caused the bell to show the same notification twice.
 * Now IN_APP and EMAIL are sent via separate targeted methods so the caller
 * controls which channels fire — and the in-app bell only receives one record.
 */
@Service
@Primary
public class AppraisalNotificationServiceImpl implements AppraisalNotificationService {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;

    private static final String FROM_EMAIL = "noreply@company.com";

    public AppraisalNotificationServiceImpl(NotificationService notificationService,
                                            EmployeeRepository employeeRepository) {
        this.notificationService = notificationService;
        this.employeeRepository  = employeeRepository;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String emailOf(String empId) {
        if (empId == null) return null;
        return employeeRepository.findByEmpId(empId)
                .map(Employee::getEmail).orElse(null);
    }

    private String nameOf(String empId) {
        if (empId == null) return empId;
        return employeeRepository.findByEmpId(empId)
                .map(Employee::getName).orElse(empId);
    }

    /**
     * Sends a single IN_APP notification (shown in the bell).
     * Email is sent separately via sendEmail() only when an address is available.
     * This prevents two records appearing in the notification bell.
     */
    private void sendInApp(String userId, EventType eventType, String subject, String body) {
        notificationService.createNotification(
                userId, FROM_EMAIL, null,
                eventType, Channel.IN_APP,
                subject, body);
    }

    private void sendEmail(String userId, String toEmail, EventType eventType, String subject, String body) {
        if (toEmail != null && !toEmail.isBlank()) {
            notificationService.createNotification(
                    userId, FROM_EMAIL, toEmail,
                    eventType, Channel.EMAIL,
                    subject, body);
        }
    }

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void notifyEmployeeSubmitted(String employeeId, String cycleLabel, String l1ApproverId) {
        String toEmail = emailOf(employeeId);
        String subject = "Appraisal Submitted – " + cycleLabel;
        String body    = "Hi " + nameOf(employeeId) + ",\n\n"
                + "Your self-appraisal for **" + cycleLabel + "** has been submitted successfully. "
                + "It is now pending review by your manager.\n\n"
                + "You will be notified once the review is complete.\n\n"
                + "Regards,\nHR Team";
        sendInApp(employeeId, EventType.APPRAISAL_SUBMITTED, subject, body);
        sendEmail(employeeId, toEmail, EventType.APPRAISAL_SUBMITTED, subject, body);
    }

    @Override
    public void notifyL1PendingReview(String l1ApproverId, String employeeId,
                                      String employeeName, String cycleLabel) {
        String toEmail = emailOf(l1ApproverId);
        String subject = "Action Required: Appraisal Review – " + employeeName;
        String body    = "Hi " + nameOf(l1ApproverId) + ",\n\n"
                + employeeName + " (" + employeeId + ") has submitted their self-appraisal for "
                + "**" + cycleLabel + "**.\n\n"
                + "Please log in to review and provide your remarks and rating.\n\n"
                + "Regards,\nHR Team";
        sendInApp(l1ApproverId, EventType.APPRAISAL_L1_PENDING_REVIEW, subject, body);
        sendEmail(l1ApproverId, toEmail, EventType.APPRAISAL_L1_PENDING_REVIEW, subject, body);
    }

    @Override
    public void notifyEmployeeL1Rejected(String employeeId, String cycleLabel, String remarks) {
        String toEmail = emailOf(employeeId);
        String subject = "Appraisal Returned for Revision – " + cycleLabel;
        String body    = "Hi " + nameOf(employeeId) + ",\n\n"
                + "Your self-appraisal for **" + cycleLabel + "** has been returned for revision by your manager.\n\n"
                + "Manager Remarks: " + (remarks != null ? remarks : "–") + "\n\n"
                + "Please review the feedback, update your appraisal, and resubmit.\n\n"
                + "Regards,\nHR Team";
        sendInApp(employeeId, EventType.APPRAISAL_L1_REJECTED, subject, body);
        sendEmail(employeeId, toEmail, EventType.APPRAISAL_L1_REJECTED, subject, body);
    }

    @Override
    public void notifyL2PendingReview(String l2ApproverId, String employeeId,
                                      String employeeName, String cycleLabel) {
        String toEmail = emailOf(l2ApproverId);
        String subject = "Action Required: Final Appraisal Review – " + employeeName;
        String body    = "Hi " + nameOf(l2ApproverId) + ",\n\n"
                + "The appraisal of " + employeeName + " (" + employeeId + ") for **" + cycleLabel + "** "
                + "has been approved by the first-level reviewer and is now pending your final review.\n\n"
                + "Please log in to provide your final remarks, ratings, and publish the result.\n\n"
                + "Regards,\nHR Team";
        sendInApp(l2ApproverId, EventType.APPRAISAL_L2_PENDING_REVIEW, subject, body);
        sendEmail(l2ApproverId, toEmail, EventType.APPRAISAL_L2_PENDING_REVIEW, subject, body);
    }

    @Override
    public void notifyEmployeePublished(String employeeId, String cycleLabel) {
        String toEmail = emailOf(employeeId);
        String subject = "Appraisal Results Published – " + cycleLabel;
        String body    = "Hi " + nameOf(employeeId) + ",\n\n"
                + "Your appraisal results for **" + cycleLabel + "** have been published. "
                + "You can now log in to view your ratings, section averages, and manager remarks.\n\n"
                + "Regards,\nHR Team";
        sendInApp(employeeId, EventType.APPRAISAL_PUBLISHED, subject, body);
        sendEmail(employeeId, toEmail, EventType.APPRAISAL_PUBLISHED, subject, body);
    }
}
