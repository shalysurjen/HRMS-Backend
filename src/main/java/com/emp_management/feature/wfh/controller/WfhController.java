package com.emp_management.feature.wfh.controller;
import com.emp_management.feature.wfh.dto.WfhRequestDTO;
import com.emp_management.feature.wfh.dto.WfhEditRequestDTO;
import com.emp_management.feature.wfh.dto.WfhResponseDTO;
import com.emp_management.feature.wfh.service.WfhApprovalService;
import com.emp_management.feature.wfh.service.WfhService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/wfh")
public class WfhController {

    private final WfhService wfhService;
    private final WfhApprovalService wfhApprovalService;

    public WfhController(WfhService wfhService, WfhApprovalService wfhApprovalService) {
        this.wfhService         = wfhService;
        this.wfhApprovalService = wfhApprovalService;
    }

    // ── APPLY (multipart — supports optional file attachment) ─────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WfhResponseDTO> apply(
            @ModelAttribute WfhRequestDTO request) {
        return ResponseEntity.ok(wfhService.applyWfh(request));
    }

    // ── GET MY WFH APPLICATIONS ───────────────────────────────────
    @GetMapping("/employee/{empId}")
    public ResponseEntity<List<WfhResponseDTO>> getMyApplications(
            @PathVariable String empId) {
        return ResponseEntity.ok(wfhService.getMyWfhApplications(empId));
    }

    // ── GET TOTAL WFH DAYS FOR EMPLOYEE ──────────────────────────
    @GetMapping("/employee/{empId}/total-days")
    public ResponseEntity<BigDecimal> getTotalDays(@PathVariable String empId) {
        return ResponseEntity.ok(wfhService.getTotalWfhDays(empId));
    }

    // ── GET PENDING FOR APPROVER ──────────────────────────────────
    @GetMapping("/approver/{approverId}/pending")
    public ResponseEntity<List<WfhResponseDTO>> getPending(
            @PathVariable String approverId) {
        return ResponseEntity.ok(wfhApprovalService.getPendingForApprover(approverId));
    }

    // ── APPROVE ───────────────────────────────────────────────────
    @PutMapping("/{wfhId}/approve")
    public ResponseEntity<WfhResponseDTO> approve(
            @PathVariable Long wfhId,
            @RequestParam String approverId,
            @RequestParam String comments) {
        return ResponseEntity.ok(wfhApprovalService.approveWfh(wfhId, approverId, comments));
    }

    // ── REJECT ────────────────────────────────────────────────────
    @PutMapping("/{wfhId}/reject")
    public ResponseEntity<WfhResponseDTO> reject(
            @PathVariable Long wfhId,
            @RequestParam String approverId,
            @RequestParam String comments) {
        return ResponseEntity.ok(wfhApprovalService.rejectWfh(wfhId, approverId, comments));
    }

    // ── CANCEL ────────────────────────────────────────────────────
    @PutMapping("/{wfhId}/cancel")
    public ResponseEntity<WfhResponseDTO> cancel(
            @PathVariable Long wfhId,
            @RequestParam String empId) {
        return ResponseEntity.ok(wfhService.cancelWfh(wfhId, empId));
    }
    // ── EDIT — paste this into WfhController.java ─────────────────
    @PutMapping(value = "/{wfhId}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WfhResponseDTO> edit(
            @PathVariable Long wfhId,
            @ModelAttribute WfhEditRequestDTO request) {
        return ResponseEntity.ok(wfhService.editWfh(wfhId, request));
    }
    @GetMapping("/{wfhId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long wfhId) {
        return wfhService.getAttachment(wfhId);
    }

}
