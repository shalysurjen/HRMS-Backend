package com.emp_management.feature.apprasial.controller;

import com.emp_management.feature.apprasial.dto.*;
import com.emp_management.feature.apprasial.service.AppraisalService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * AppraisalController
 *
 * Access control (enforced in frontend via AppRoutes + role guards):
 *   /save, /employee/**        → EMPLOYEE role only
 *   /approver/**               → MANAGER / CTO / COO / CEO / CFO / TEAM_LEADER
 *   /admin/**                  → ADMIN / HR only
 *
 * Flow:
 *   1. Employee fills form → POST /save (submit=true)
 *      → Backend assigns firstApproverId = employee.reportingId (L1 = Reporting Manager)
 *      → Status: SUBMITTED
 *
 *   2. L1 Manager reviews → POST /{id}/remarks (approve=true)
 *      → Status: L1_APPROVED
 *      → Notification sent to finalApproverId (L2 = L1's reporting manager)
 *
 *   3. L2 (COO/CEO) reviews → POST /{id}/remarks (publish=true)
 *      → Status: PUBLISHED
 *      → Employee can view their full result via GET /employee/{id}/cycle/{id}/published
 *
 *   4. Admin/HR → GET /admin/export  (Excel)
 *              → GET /admin/export/pdf  (PDF)
 */
@RestController
@RequestMapping("/v1/appraisal")
public class AppraisalController {

    private final AppraisalService appraisalService;

    public AppraisalController(AppraisalService appraisalService) {
        this.appraisalService = appraisalService;
    }

    // ── Cycles ───────────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    public ResponseEntity<List<AppraisalCycleDTO>> getCycles() {
        return ResponseEntity.ok(appraisalService.getAllCycles());
    }

    @GetMapping("/cycles/{cycleId}/questions")
    public ResponseEntity<List<AppraisalQuestionDTO>> getQuestions(@PathVariable Long cycleId) {
        return ResponseEntity.ok(appraisalService.getQuestions(cycleId));
    }

    // ── Employee endpoints ───────────────────────────────────────────────────

    /**
     * Get or create a self-appraisal for the employee.
     * On first call, backend sets firstApproverId = employee.reportingId (L1)
     * and finalApproverId = L1's reportingId (L2), if available.
     */
    @GetMapping("/employee/{employeeId}/cycle/{cycleId}")
    public ResponseEntity<AppraisalDetailDTO> getOrCreate(
            @PathVariable String employeeId, @PathVariable Long cycleId) {
        return ResponseEntity.ok(appraisalService.getOrCreate(employeeId, cycleId));
    }

    /**
     * Save draft or final submit.
     * On submit=true → validates required questions (Suggestions section always optional,
     * last question optional if isRequired=false), then sets status SUBMITTED and
     * notifies the L1 approver (reportingManager).
     */
    @PostMapping("/save")
    public ResponseEntity<AppraisalDetailDTO> save(@RequestBody SaveAppraisalRequest req) {
        return ResponseEntity.ok(appraisalService.saveAnswers(req));
    }

    /**
     * Employee views their published result (only accessible after PUBLISHED status).
     * Returns full form with L1 and L2 remarks + ratings.
     */
    @GetMapping("/employee/{employeeId}/cycle/{cycleId}/published")
    public ResponseEntity<AppraisalDetailDTO> getPublished(
            @PathVariable String employeeId, @PathVariable Long cycleId) {
        return ResponseEntity.ok(appraisalService.getPublished(employeeId, cycleId));
    }

    /**
     * Employee: download their own PUBLISHED appraisal as PDF.
     * Only works if status = PUBLISHED. Returns 403 otherwise.
     */
    @GetMapping("/employee/{employeeId}/cycle/{cycleId}/export/pdf")
    public void exportEmployeePdf(
            @PathVariable String employeeId,
            @PathVariable Long cycleId,
            HttpServletResponse response) throws IOException {
        appraisalService.exportEmployeePdf(employeeId, cycleId, response);
    }

    /**
     * Employee: download their own PUBLISHED appraisal as Excel.
     * Only works if status = PUBLISHED. Returns 403 otherwise.
     */
    @GetMapping("/employee/{employeeId}/cycle/{cycleId}/export/excel")
    public void exportEmployeeExcel(
            @PathVariable String employeeId,
            @PathVariable Long cycleId,
            HttpServletResponse response) throws IOException {
        appraisalService.exportEmployeeExcel(employeeId, cycleId, response);
    }

    /**
     * Employee's past appraisal history (all cycles).
     */
    @GetMapping("/employee/{employeeId}/history")
    public ResponseEntity<List<EmployeeAppraisalSummaryDTO>> getHistory(
            @PathVariable String employeeId) {
        return ResponseEntity.ok(appraisalService.getMyHistory(employeeId));
    }

    // ── Approver endpoints ───────────────────────────────────────────────────

    /** L1 pending queue (SUBMITTED status, firstApproverId = this manager). */
    @GetMapping("/approver/l1/{approverId}/pending")
    public ResponseEntity<List<EmployeeAppraisalSummaryDTO>> l1Pending(
            @PathVariable String approverId) {
        return ResponseEntity.ok(appraisalService.getPendingForL1(approverId));
    }

    /** L2 pending queue (L1_APPROVED / FINAL_REVIEW status, finalApproverId = this approver). */
    @GetMapping("/approver/l2/{approverId}/pending")
    public ResponseEntity<List<EmployeeAppraisalSummaryDTO>> l2Pending(
            @PathVariable String approverId) {
        return ResponseEntity.ok(appraisalService.getPendingForL2(approverId));
    }

    /** Combined L1 + L2 pending for a single approver (used by MANAGER/CTO/TEAM_LEADER). */
    @GetMapping("/approver/{approverId}/pending")
    public ResponseEntity<List<EmployeeAppraisalSummaryDTO>> combinedPending(
            @PathVariable String approverId) {
        return ResponseEntity.ok(appraisalService.getPendingForApprover(approverId));
    }

    /** Full appraisal detail for approver review (includes all answers + remarks). */
    @GetMapping("/{appraisalId}/detail")
    public ResponseEntity<AppraisalDetailDTO> getDetail(@PathVariable Long appraisalId) {
        return ResponseEntity.ok(appraisalService.getForApprover(appraisalId));
    }

    /**
     * Submit remarks and decision.
     * - approverLevel=L1, approve=true  → L1_APPROVED, notifies L2
     * - approverLevel=L1, approve=false → L1_REJECTED, notifies employee
     * - approverLevel=L2, approve=true  → FINAL_REVIEW (saved but not published)
     * - approverLevel=L2, publish=true  → PUBLISHED, employee can now see result
     */
    @PostMapping("/{appraisalId}/remarks")
    public ResponseEntity<AppraisalDetailDTO> addRemarks(
            @PathVariable Long appraisalId, @RequestBody RemarkRequest req) {
        return ResponseEntity.ok(appraisalService.addRemarks(appraisalId, req));
    }

    // ── Admin / HR endpoints ─────────────────────────────────────────────────

    /**
     * All appraisals across the organisation.
     * Optional cycleId filter. Used by ADMIN and HR roles.
     */
    @GetMapping("/admin/all")
    public ResponseEntity<List<EmployeeAppraisalSummaryDTO>> getAllAppraisals(
            @RequestParam(required = false) Long cycleId) {
        return ResponseEntity.ok(appraisalService.getAllAppraisals(cycleId));
    }

    /**
     * Excel export.
     * GET /v1/appraisal/admin/export?cycleId=1  (cycleId optional)
     * Streams an .xlsx file with all appraisal data (employee info, answers, L1/L2 remarks & ratings).
     */
    @GetMapping("/admin/export")
    public void exportExcel(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false, defaultValue = "ALL") String statusFilter,
            HttpServletResponse response) throws IOException {
        appraisalService.exportToExcel(cycleId, statusFilter, response);
    }

    /**
     * PDF export.
     * GET /v1/appraisal/admin/export/pdf?cycleId=1  (cycleId optional)
     * Streams a PDF file with all appraisal data — one appraisal per page block.
     * Each appraisal includes: employee info, all questions with self-answers,
     * L1 and L2 remarks + ratings, and overall average.
     */
    @GetMapping("/admin/export/pdf")
    public void exportPdf(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false, defaultValue = "ALL") String statusFilter,
            HttpServletResponse response) throws IOException {
        appraisalService.exportToPdf(cycleId, statusFilter, response);
    }

    /**
     * Save draft remarks for approver — persists remarks without changing status or sending notifications.
     * Used by L1 and L2 to save work-in-progress before final submit.
     */
    @PostMapping("/{appraisalId}/remarks/draft")
    public ResponseEntity<AppraisalDetailDTO> saveDraftRemarks(
            @PathVariable Long appraisalId, @RequestBody RemarkRequest req) {
        req.setDraftOnly(true);
        return ResponseEntity.ok(appraisalService.addRemarks(appraisalId, req));
    }
}