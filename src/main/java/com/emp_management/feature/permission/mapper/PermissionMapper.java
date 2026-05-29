package com.emp_management.feature.permission.mapper;

import com.emp_management.feature.permission.dto.PermissionResponseDTO;
import com.emp_management.feature.permission.entity.Permission;

public class PermissionMapper {

    public static PermissionResponseDTO toDTO(Permission p) {
        PermissionResponseDTO dto = new PermissionResponseDTO();

        dto.setId(p.getId());
        dto.setEmployeeId(p.getEmployeeId());
        dto.setEmployeeName(p.getEmployeeName());
        dto.setPermissionDate(p.getPermissionDate());
        dto.setStartTime(p.getStartTime());
        dto.setEndTime(p.getEndTime());
        dto.setDurationMinutes(p.getDurationMinutes());
        dto.setDurationFormatted(formatDuration(p.getDurationMinutes()));
        dto.setReason(p.getReason());
        dto.setStatus(p.getStatus());
        dto.setRejectionReason(p.getRejectionReason());

        // ── Approval chain (unchanged) ─────────────────────────────
        dto.setRequiredApprovalLevels(p.getRequiredApprovalLevels());
        dto.setCurrentApprovalLevel(p.getCurrentApprovalLevel());
        dto.setCurrentApproverId(p.getCurrentApproverId());

        dto.setFirstApproverId(p.getFirstApproverId());
        dto.setFirstApproverDecision(p.getFirstApproverDecision());
        dto.setFirstApproverDecidedAt(p.getFirstApproverDecidedAt());

        dto.setSecondApproverId(p.getSecondApproverId());
        dto.setSecondApproverDecision(p.getSecondApproverDecision());
        dto.setSecondApproverDecidedAt(p.getSecondApproverDecidedAt());

        dto.setApprovedBy(p.getApprovedBy());
        dto.setApprovedRole(p.getApprovedRole());
        dto.setApprovedAt(p.getApprovedAt());

        // ── Audit (unchanged) ──────────────────────────────────────
        dto.setCreatedAt(p.getCreatedAt());
        dto.setCreatedBy(p.getCreatedBy());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setUpdatedBy(p.getUpdatedBy());

        // ── NEW: attachment fields ─────────────────────────────────
        // Maps the 5 new columns to the DTO so frontend can
        // display filename, type, and size
        dto.setAttachmentFileName(p.getAttachmentFileName());
        dto.setAttachmentOriginalName(p.getAttachmentOriginalName());
        dto.setAttachmentContentType(p.getAttachmentContentType());
        dto.setAttachmentPath(p.getAttachmentPath());
        dto.setAttachmentSize(p.getAttachmentSize());

        return dto;
    }

    private static String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return "0 mins";
        int hrs  = minutes / 60;
        int mins = minutes % 60;
        if (hrs > 0 && mins > 0)
            return hrs + " hr" + (hrs > 1 ? "s" : "")
                    + " " + mins + " min" + (mins > 1 ? "s" : "");
        if (hrs > 0)
            return hrs + " hr" + (hrs > 1 ? "s" : "");
        return mins + " min" + (mins > 1 ? "s" : "");
    }
}