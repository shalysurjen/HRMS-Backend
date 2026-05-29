package com.emp_management.feature.wfh.mapper;

import com.emp_management.feature.wfh.dto.WfhResponseDTO;
import com.emp_management.feature.wfh.entity.WfhApplication;

public class WfhMapper {

    public static WfhResponseDTO toDTO(WfhApplication w) {
        WfhResponseDTO dto = new WfhResponseDTO();

        dto.setId(w.getId());
        dto.setEmployeeId(w.getEmployeeId());
        dto.setEmployeeName(w.getEmployeeName());

        dto.setStartDate(w.getStartDate());
        dto.setEndDate(w.getEndDate());
        dto.setStartDateHalfDayType(w.getStartDateHalfDayType());
        dto.setEndDateHalfDayType(w.getEndDateHalfDayType());
        dto.setTotalDays(w.getTotalDays());
        dto.setReason(w.getReason());

        dto.setStatus(w.getStatus());
        dto.setRejectionReason(w.getRejectionReason());

        dto.setRequiredApprovalLevels(w.getRequiredApprovalLevels());
        dto.setCurrentApprovalLevel(w.getCurrentApprovalLevel());
        dto.setCurrentApproverId(w.getCurrentApproverId());

        dto.setFirstApproverId(w.getFirstApproverId());
        dto.setFirstApproverDecision(w.getFirstApproverDecision());
        dto.setFirstApproverDecidedAt(w.getFirstApproverDecidedAt());

        dto.setSecondApproverId(w.getSecondApproverId());
        dto.setSecondApproverDecision(w.getSecondApproverDecision());
        dto.setSecondApproverDecidedAt(w.getSecondApproverDecidedAt());

        dto.setApprovedBy(w.getApprovedBy());
        dto.setApprovedRole(w.getApprovedRole());
        dto.setApprovedAt(w.getApprovedAt());

        dto.setAttachmentFileName(w.getAttachmentFileName());
        dto.setAttachmentOriginalName(w.getAttachmentOriginalName());
        dto.setAttachmentContentType(w.getAttachmentContentType());
        dto.setAttachmentPath(w.getAttachmentPath());
        dto.setAttachmentSize(w.getAttachmentSize());

        dto.setCreatedAt(w.getCreatedAt());
        dto.setCreatedBy(w.getCreatedBy());
        dto.setUpdatedAt(w.getUpdatedAt());
        dto.setUpdatedBy(w.getUpdatedBy());

        return dto;
    }
}
