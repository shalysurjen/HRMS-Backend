package com.emp_management.feature.apprasial.service;

/**
 * Contract for sending appraisal lifecycle notifications.
 * Implement this interface (e.g. via email, push, or in-app) and register it as
 * a Spring bean.  The default {@link NoOpAppraisalNotificationService} is a
 * no-op placeholder that prevents injection failures when no real impl exists.
 */
public interface AppraisalNotificationService {

    /**
     * Notify the employee that their appraisal has been submitted successfully.
     *
     * @param employeeId   the submitting employee's ID
     * @param cycleLabel   human-readable cycle name (e.g. "FY 2025–26")
     * @param l1ApproverId the L1 approver who now needs to act
     */
    void notifyEmployeeSubmitted(String employeeId, String cycleLabel, String l1ApproverId);

    /**
     * Notify the L1 approver that a new appraisal is pending their review.
     *
     * @param l1ApproverId  approver's employee ID
     * @param employeeId    the employee whose appraisal needs review
     * @param employeeName  display name for the notification body
     * @param cycleLabel    appraisal cycle label
     */
    void notifyL1PendingReview(String l1ApproverId, String employeeId,
                               String employeeName, String cycleLabel);

    /**
     * Notify the employee their appraisal was rejected by L1 (needs correction).
     *
     * @param employeeId  the employee being notified
     * @param cycleLabel  appraisal cycle label
     * @param remarks     rejection reason / feedback from L1
     */
    void notifyEmployeeL1Rejected(String employeeId, String cycleLabel, String remarks);

    /**
     * Notify the L2 approver that an appraisal has been approved by L1 and is
     * ready for final review.
     *
     * @param l2ApproverId  L2 approver's employee ID
     * @param employeeId    the employee whose appraisal is ready
     * @param employeeName  display name
     * @param cycleLabel    appraisal cycle label
     */
    void notifyL2PendingReview(String l2ApproverId, String employeeId,
                               String employeeName, String cycleLabel);

    /**
     * Notify the employee that their appraisal has been published (results visible).
     *
     * @param employeeId  the employee being notified
     * @param cycleLabel  appraisal cycle label
     */
    void notifyEmployeePublished(String employeeId, String cycleLabel);
}
