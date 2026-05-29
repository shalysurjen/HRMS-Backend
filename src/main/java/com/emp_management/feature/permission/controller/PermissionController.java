package com.emp_management.feature.permission.controller;

import com.emp_management.feature.permission.dto.PermissionRequestDTO;
import com.emp_management.feature.permission.dto.PermissionResponseDTO;
import com.emp_management.feature.permission.service.PermissionApprovalService;
import com.emp_management.feature.permission.service.PermissionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PutMapping;
import java.util.List;
import org.springframework.core.io.Resource;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionApprovalService permissionApprovalService;

    public PermissionController(
            PermissionService permissionService,
            PermissionApprovalService permissionApprovalService) {
        this.permissionService         = permissionService;
        this.permissionApprovalService = permissionApprovalService;
    }

    // ── CHANGED: @RequestBody → @ModelAttribute
    //             consumes = MULTIPART_FORM_DATA_VALUE
    //             Required because PermissionRequestDTO now contains
    //             a MultipartFile field which @RequestBody cannot handle
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PermissionResponseDTO> apply(
            @ModelAttribute PermissionRequestDTO request) {
        return ResponseEntity.ok(
                permissionService.applyPermission(request));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @GetMapping("/employee/{empId}")
    public ResponseEntity<List<PermissionResponseDTO>> getMyPermissions(
            @PathVariable String empId) {
        return ResponseEntity.ok(
                permissionService.getMyPermissions(empId));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @GetMapping("/approver/{approverId}/pending")
    public ResponseEntity<List<PermissionResponseDTO>> getPending(
            @PathVariable String approverId) {
        return ResponseEntity.ok(
                permissionApprovalService.getPendingForApprover(approverId));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @PutMapping("/{permissionId}/approve")
    public ResponseEntity<PermissionResponseDTO> approve(
            @PathVariable Long permissionId,
            @RequestParam String approverId,
            @RequestParam String comments) {
        return ResponseEntity.ok(
                permissionApprovalService.approvePermission(
                        permissionId, approverId, comments));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @PutMapping("/{permissionId}/reject")
    public ResponseEntity<PermissionResponseDTO> reject(
            @PathVariable Long permissionId,
            @RequestParam String approverId,
            @RequestParam String comments) {
        return ResponseEntity.ok(
                permissionApprovalService.rejectPermission(
                        permissionId, approverId, comments));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @PutMapping("/{permissionId}/cancel")
    public ResponseEntity<PermissionResponseDTO> cancel(
            @PathVariable Long permissionId,
            @RequestParam String empId) {
        return ResponseEntity.ok(
                permissionService.cancelPermission(permissionId, empId));
    }

    // ── Unchanged ─────────────────────────────────────────────────
    @PutMapping("/{permissionId}/update")
    public ResponseEntity<PermissionResponseDTO> update(
            @PathVariable Long permissionId,
            @RequestParam String empId,
            @RequestBody PermissionRequestDTO request) {
        return ResponseEntity.ok(
                permissionService.editPermission(permissionId, empId, request));
    }
    // GET /api/permissions/{permissionId}/attachment
    @GetMapping("/{permissionId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long permissionId) {
        return permissionService.getAttachment(permissionId);
    }


}