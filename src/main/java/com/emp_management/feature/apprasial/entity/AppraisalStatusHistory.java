package com.emp_management.feature.apprasial.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appraisal_status_history")
public class AppraisalStatusHistory {

    /**
     * Describes the semantic action that caused the status transition.
     * E.g.  SUBMITTED, L1_APPROVED, L1_REJECTED, L2_APPROVED, PUBLISHED, etc.
     * Kept as a plain String so it can hold free-form or enum-derived values
     * without a strict DB enum constraint.
     */
    public enum ActionType {
        SUBMITTED,
        L1_APPROVED,
        L1_REJECTED,
        L2_APPROVED,
        L2_REJECTED,
        PUBLISHED,
        CLOSED,
        DRAFT_SAVED,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private SelfAppraisal appraisal;

    private String fromStatus;
    private String toStatus;

    /** Employee-ID / system ID of the actor — same as the original changedBy. */
    private String changedBy;

    /** NEW: human-readable display name of the actor (e.g. "Ravi Kumar"). */
    private String changedByName;

    /** NEW: semantic action type for filtering / display logic. */
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    private LocalDateTime changedAt;

    public AppraisalStatusHistory() {}

    @PrePersist void onCreate() { changedAt = LocalDateTime.now(); }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SelfAppraisal getAppraisal() { return appraisal; }
    public void setAppraisal(SelfAppraisal appraisal) { this.appraisal = appraisal; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getChangedAt() { return changedAt; }
}
