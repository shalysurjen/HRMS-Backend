package com.emp_management.feature.apprasial.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Default no-op implementation of {@link AppraisalNotificationService}.
 * Replace (or supplement) this bean with a real email / push implementation.
 * The {@code @ConditionalOnMissingBean} ensures this is only registered when
 * no other {@link AppraisalNotificationService} bean is present.
 */
@Service
@ConditionalOnMissingBean(value = AppraisalNotificationService.class,
                          ignored = NoOpAppraisalNotificationService.class)
public class NoOpAppraisalNotificationService implements AppraisalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NoOpAppraisalNotificationService.class);

    @Override
    public void notifyEmployeeSubmitted(String employeeId, String cycleLabel, String l1ApproverId) {
        log.info("[Notification-NOOP] Employee {} submitted appraisal for cycle '{}'. L1: {}",
                employeeId, cycleLabel, l1ApproverId);
    }

    @Override
    public void notifyL1PendingReview(String l1ApproverId, String employeeId,
                                       String employeeName, String cycleLabel) {
        log.info("[Notification-NOOP] L1 {} has pending review for {} ({}) – cycle '{}'",
                l1ApproverId, employeeName, employeeId, cycleLabel);
    }

    @Override
    public void notifyEmployeeL1Rejected(String employeeId, String cycleLabel, String remarks) {
        log.info("[Notification-NOOP] Employee {} appraisal rejected in cycle '{}'. Remarks: {}",
                employeeId, cycleLabel, remarks);
    }

    @Override
    public void notifyL2PendingReview(String l2ApproverId, String employeeId,
                                       String employeeName, String cycleLabel) {
        log.info("[Notification-NOOP] L2 {} has pending review for {} ({}) – cycle '{}'",
                l2ApproverId, employeeName, employeeId, cycleLabel);
    }

    @Override
    public void notifyEmployeePublished(String employeeId, String cycleLabel) {
        log.info("[Notification-NOOP] Employee {} appraisal published for cycle '{}'",
                employeeId, cycleLabel);
    }
}
