package com.emp_management.feature.apprasial.enums;

public enum AppraisalStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,       // L1 has opened the form
    L1_APPROVED,
    L1_REJECTED,
    L2_UNDER_REVIEW,    // L2 clicked "Start Review" — moved to Pending tab
    L2_REJECTED,
    FINAL_REVIEW,
    PUBLISHED,
    CLOSED
}
