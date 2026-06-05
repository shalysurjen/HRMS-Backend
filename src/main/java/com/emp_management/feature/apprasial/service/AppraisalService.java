package com.emp_management.feature.apprasial.service;

import com.emp_management.feature.apprasial.dto.*;
import com.emp_management.feature.apprasial.entity.*;
import com.emp_management.feature.apprasial.enums.AppraisalStatus;
import com.emp_management.feature.apprasial.repository.*;
import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
@Transactional
public class AppraisalService {

    // Section that is treated as "Suggestion" — questions here are never rated
    private static final String SUGGESTION_SECTION = "Suggestions";

    private static final List<String> SECTION_ORDER = List.of(
            "Performance",
            "Skills and Development",
            "Collaboration and Teamwork",
            "Innovation and Problem-Solving",
            "Communication and Feedback",
            SUGGESTION_SECTION
    );

    private final AppraisalCycleRepository           cycleRepo;
    private final AppraisalQuestionRepository        questionRepo;
    private final SelfAppraisalRepository            appraisalRepo;
    private final SelfAppraisalAnswerRepository      answerRepo;
    private final AppraisalRemarkRepository          remarkRepo;
    private final AppraisalStatusHistoryRepository   historyRepo;
    private final EmployeeRepository                 employeeRepo;
    private final AppraisalNotificationService       notificationService;
    private final AppraisalProjectRepository         projectRepo;

    public AppraisalService(AppraisalCycleRepository cycleRepo,
                            AppraisalQuestionRepository questionRepo,
                            SelfAppraisalRepository appraisalRepo,
                            SelfAppraisalAnswerRepository answerRepo,
                            AppraisalRemarkRepository remarkRepo,
                            AppraisalStatusHistoryRepository historyRepo,
                            EmployeeRepository employeeRepo,
                            AppraisalNotificationService notificationService,
                            AppraisalProjectRepository projectRepo) {
        this.cycleRepo           = cycleRepo;
        this.questionRepo        = questionRepo;
        this.appraisalRepo       = appraisalRepo;
        this.answerRepo          = answerRepo;
        this.remarkRepo          = remarkRepo;
        this.historyRepo         = historyRepo;
        this.employeeRepo        = employeeRepo;
        this.notificationService = notificationService;
        this.projectRepo         = projectRepo;
    }

    // ── Cycles ────────────────────────────────────────────────────────────────
    public List<AppraisalCycleDTO> getAllCycles() {
        return cycleRepo.findAllByOrderByStartYearDesc().stream().map(c -> {
            AppraisalCycleDTO dto = new AppraisalCycleDTO();
            dto.setId(c.getId());
            dto.setCycleLabel(c.getCycleLabel());
            dto.setStartYear(c.getStartYear());
            dto.setEndYear(c.getEndYear());
            dto.setActive(c.isActive());
            dto.setOpen(c.isOpen());
            return dto;
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toggle isActive or isOpen on a cycle.
    // If setting isActive=true, all other cycles are set to isActive=false first.
    // ─────────────────────────────────────────────────────────────────────────
    public AppraisalCycleDTO toggleCycleField(Long cycleId, String field, boolean value) {
        AppraisalCycle cycle = cycleRepo.findById(cycleId)
                .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + cycleId));

        if ("isActive".equalsIgnoreCase(field)) {
            if (value) {
                // Deactivate all others first
                cycleRepo.findAll().forEach(c -> {
                    if (!c.getId().equals(cycleId) && c.isActive()) {
                        c.setActive(false);
                        cycleRepo.save(c);
                    }
                });
            }
            cycle.setActive(value);
        } else if ("isOpen".equalsIgnoreCase(field)) {
            cycle.setOpen(value);
        } else {
            throw new IllegalArgumentException("Unknown field: " + field + ". Use 'isActive' or 'isOpen'.");
        }

        AppraisalCycle saved = cycleRepo.save(cycle);

        AppraisalCycleDTO dto = new AppraisalCycleDTO();
        dto.setId(saved.getId());
        dto.setCycleLabel(saved.getCycleLabel());
        dto.setStartYear(saved.getStartYear());
        dto.setEndYear(saved.getEndYear());
        dto.setActive(saved.isActive());
        dto.setOpen(saved.isOpen());
        return dto;
    }

    // ── Questions ─────────────────────────────────────────────────────────────
    public List<AppraisalQuestionDTO> getQuestions(Long cycleId) {
        return questionRepo.findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(cycleId)
                .stream().map(q -> {
                    AppraisalQuestionDTO dto = new AppraisalQuestionDTO();
                    dto.setId(q.getId());
                    dto.setSection(q.getSection());
                    dto.setSortOrder(q.getSortOrder());
                    dto.setQuestionText(q.getQuestionText());
                    dto.setInputType(q.getInputType());
                    dto.setRequired(q.isRequired());
                    return dto;
                }).collect(Collectors.toList());
    }

    // ── Get or Create ─────────────────────────────────────────────────────────
    public AppraisalDetailDTO getOrCreate(String employeeId, Long cycleId) {
        AppraisalCycle cycle = cycleRepo.findById(cycleId)
                .orElseThrow(() -> new EntityNotFoundException("Cycle not found"));

        SelfAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndCycle_Id(employeeId, cycleId)
                .orElseGet(() -> {
                    Employee emp = employeeRepo.findByEmpId(employeeId)
                            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

                    SelfAppraisal a = new SelfAppraisal();
                    a.setEmployeeId(employeeId);
                    a.setCycle(cycle);
                    a.setStatus(AppraisalStatus.DRAFT);

                    String l1Id = emp.getReportingId();
                    a.setFirstApproverId(l1Id);

                    if (l1Id != null) {
                        employeeRepo.findByEmpId(l1Id).ifPresent(manager -> {
                            String l2Id = manager.getReportingId();
                            if (l2Id != null && !l2Id.equals(employeeId)) {
                                a.setFinalApproverId(l2Id);
                            }
                        });
                    }

                    return appraisalRepo.save(a);
                });

        return buildDetailDTO(appraisal, false);
    }

    // ── Save / Submit ─────────────────────────────────────────────────────────
    public AppraisalDetailDTO saveAnswers(SaveAppraisalRequest req) {
        SelfAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndCycle_Id(req.getEmployeeId(), req.getCycleId())
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found"));

        if (appraisal.getStatus() != AppraisalStatus.DRAFT
                && appraisal.getStatus() != AppraisalStatus.L1_REJECTED) {
            throw new IllegalStateException(
                    "Appraisal already submitted. Re-submission is not allowed. Current status: "
                            + appraisal.getStatus());
        }

        if (req.isSubmit()) {
            List<AppraisalQuestion> allQuestions = questionRepo
                    .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(req.getCycleId());

            Map<Long, AnswerDTO> submittedMap = req.getAnswers() == null
                    ? Collections.emptyMap()
                    : req.getAnswers().stream()
                    .collect(Collectors.toMap(AnswerDTO::getQuestionId, a -> a, (a, b) -> b));

            AppraisalQuestion lastQuestion = allQuestions.isEmpty()
                    ? null : allQuestions.get(allQuestions.size() - 1);

            List<String> missing = new ArrayList<>();
            for (AppraisalQuestion q : allQuestions) {
                boolean isLastOptional = q.equals(lastQuestion) && !q.isRequired();
                if (isLastOptional) continue;

                if (q.isRequired()) {
                    AnswerDTO ans = submittedMap.get(q.getId());
                    boolean hasText   = ans != null && ans.getAnswerText() != null && !ans.getAnswerText().isBlank();
                    boolean hasRating = ans != null && ans.getSelfRating() != null;
                    boolean answered = switch (q.getInputType()) {
                        case RATING             -> hasRating;
                        case TEXTAREA, TEXT     -> hasText || hasRating;
                    };
                    if (!answered) {
                        missing.add(q.getQuestionText().length() > 50
                                ? q.getQuestionText().substring(0, 50) + "…"
                                : q.getQuestionText());
                    }
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Please answer all required questions before submitting. Missing: " + missing);
            }
        }

        answerRepo.deleteByAppraisalId(appraisal.getId());

        if (req.getAnswers() != null) {
            for (AnswerDTO ans : req.getAnswers()) {
                AppraisalQuestion q = questionRepo.findById(ans.getQuestionId())
                        .orElseThrow(() -> new EntityNotFoundException("Question not found: " + ans.getQuestionId()));
                SelfAppraisalAnswer a = new SelfAppraisalAnswer();
                a.setAppraisal(appraisal);
                a.setQuestion(q);
                a.setAnswerText(ans.getAnswerText());
                a.setSelfRating(ans.getSelfRating());
                answerRepo.save(a);
            }
        }

        if (req.isSubmit()) {
            String prev = appraisal.getStatus().name();
            appraisal.setStatus(AppraisalStatus.SUBMITTED);
            appraisal.setSubmittedAt(LocalDateTime.now());
            appraisalRepo.save(appraisal);

            recordHistory(appraisal, prev, AppraisalStatus.SUBMITTED.name(),
                    req.getEmployeeId(), resolveEmployeeName(req.getEmployeeId()),
                    AppraisalStatusHistory.ActionType.SUBMITTED, "Submitted by employee");

            notificationService.notifyEmployeeSubmitted(
                    req.getEmployeeId(), appraisal.getCycle().getCycleLabel(),
                    appraisal.getFirstApproverId());

            if (appraisal.getFirstApproverId() != null) {
                String empName = resolveEmployeeName(req.getEmployeeId());
                notificationService.notifyL1PendingReview(
                        appraisal.getFirstApproverId(),
                        req.getEmployeeId(), empName,
                        appraisal.getCycle().getCycleLabel());
            }
        } else {
            appraisalRepo.save(appraisal);
        }

        return buildDetailDTO(appraisal, false);
    }

    // ── Pending L1 ────────────────────────────────────────────────────────────
    // FIX: Include UNDER_REVIEW — this status is set when L1 opens a SUBMITTED form.
    // Without it, the record disappears from L1's Pending tab the moment they open it.
    public List<EmployeeAppraisalSummaryDTO> getPendingForL1(String approverId) {
        return appraisalRepo.findByFirstApproverIdAndStatusIn(approverId,
                        Arrays.asList(AppraisalStatus.SUBMITTED, AppraisalStatus.UNDER_REVIEW, AppraisalStatus.L2_REJECTED))
                .stream().map(this::buildSummary).collect(Collectors.toList());
    }

    // ── Pending L2 ────────────────────────────────────────────────────────────
    public List<EmployeeAppraisalSummaryDTO> getPendingForL2(String approverId) {
        return appraisalRepo.findByFinalApproverIdAndStatusIn(approverId,
                        Arrays.asList(AppraisalStatus.L1_APPROVED,
                                AppraisalStatus.L2_UNDER_REVIEW,
                                AppraisalStatus.FINAL_REVIEW))
                .stream().map(this::buildSummary).collect(Collectors.toList());
    }

    public List<EmployeeAppraisalSummaryDTO> getPendingForApprover(String approverId) {
        List<EmployeeAppraisalSummaryDTO> combined = new ArrayList<>();
        combined.addAll(getPendingForL1(approverId));
        combined.addAll(getPendingForL2(approverId));
        return combined;
    }

    /**
     * FIX: Returns ALL records where this user is L2 (finalApproverId), regardless of status.
     * This allows the dashboard to show VIEW_ONLY records for L2 (e.g. SUBMITTED — L1 is still working).
     * Frontend resolveApproverLevel() then sorts them into PENDING vs VIEW_ONLY tabs correctly.
     */
    public List<EmployeeAppraisalSummaryDTO> getAllForL2Approver(String approverId) {
        // Exclude records the L2 should never see
        List<AppraisalStatus> excludeDraftAndRejected = Arrays.asList(
                AppraisalStatus.DRAFT,
                AppraisalStatus.L1_REJECTED
        );
        return appraisalRepo
                .findByFinalApproverIdAndStatusNotIn(approverId, excludeDraftAndRejected)
                .stream().map(this::buildSummary).collect(Collectors.toList());
    }

    /**
     * FIX: Returns ALL records where this user is L1 (firstApproverId), regardless of status.
     * This ensures VIEW_ONLY tab shows records L1 has already submitted (e.g. L1_APPROVED,
     * FINAL_REVIEW, PUBLISHED) — not just the ones still pending action.
     */
    public List<EmployeeAppraisalSummaryDTO> getAllForL1Approver(String approverId) {
        // Exclude records L1 should never see (not yet submitted by employee, or L1-rejected)
        List<AppraisalStatus> excludeStatuses = Arrays.asList(
                AppraisalStatus.DRAFT,
                AppraisalStatus.L1_REJECTED
        );
        return appraisalRepo
                .findByFirstApproverIdAndStatusNotIn(approverId, excludeStatuses)
                .stream().map(this::buildSummary).collect(Collectors.toList());
    }

    /**
     * FIX: Combined view for a manager who may be both L1 and L2 for different employees.
     * Returns ALL L1 records (pending + already actioned) + all L2 records.
     * Frontend resolveApproverLevel() + resolveTab() bucket them into PENDING / VIEW_ONLY / PUBLISHED.
     */
    public List<EmployeeAppraisalSummaryDTO> getAllForApprover(String approverId) {
        // Use a map to avoid duplicates (same record could appear as both L1 and L2)
        Map<Long, EmployeeAppraisalSummaryDTO> byId = new LinkedHashMap<>();
        getAllForL1Approver(approverId).forEach(dto -> byId.put(dto.getAppraisalId(), dto));
        getAllForL2Approver(approverId).forEach(dto -> byId.putIfAbsent(dto.getAppraisalId(), dto));
        return new ArrayList<>(byId.values());
    }

    // ── Approver detail ───────────────────────────────────────────────────────
    public AppraisalDetailDTO getForApprover(Long appraisalId) {
        SelfAppraisal a = appraisalRepo.findById(appraisalId)
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found"));
        return buildDetailDTO(a, true);
    }

    // ── Mark Under Review ─────────────────────────────────────────────────────
    // Called when L1 opens a SUBMITTED appraisal for review.
    // Status: SUBMITTED → UNDER_REVIEW  (so dashboard can show "reviewing in progress")
    public void markUnderReview(Long appraisalId, String approverId) {
        SelfAppraisal appraisal = appraisalRepo.findById(appraisalId)
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found: " + appraisalId));

        // Only transition from SUBMITTED; ignore if already further along
        if (appraisal.getStatus() == AppraisalStatus.SUBMITTED) {
            String prev = appraisal.getStatus().name();
            appraisal.setStatus(AppraisalStatus.UNDER_REVIEW);
            appraisalRepo.save(appraisal);
            recordHistory(appraisal, prev, AppraisalStatus.UNDER_REVIEW.name(),
                    approverId, resolveEmployeeName(approverId),
                    AppraisalStatusHistory.ActionType.L1_APPROVED, // reuse closest action type
                    "Opened for review by L1");
        }
    }

    // ── Mark L2 Under Review ─────────────────────────────────────────────────
    // Called when L2 clicks "Start Review" on a VIEW_ONLY record.
    // Status: L1_APPROVED → L2_UNDER_REVIEW  (moves record to L2 Pending tab)
    public void markL2UnderReview(Long appraisalId, String approverId) {
        SelfAppraisal appraisal = appraisalRepo.findById(appraisalId)
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found: " + appraisalId));

        if (appraisal.getStatus() == AppraisalStatus.L1_APPROVED) {
            String prev = appraisal.getStatus().name();
            appraisal.setStatus(AppraisalStatus.L2_UNDER_REVIEW);
            appraisalRepo.save(appraisal);
            recordHistory(appraisal, prev, AppraisalStatus.L2_UNDER_REVIEW.name(),
                    approverId, resolveEmployeeName(approverId),
                    AppraisalStatusHistory.ActionType.L2_APPROVED,
                    "Opened for review by L2");
        }
    }

    // ── Add remarks / decision ────────────────────────────────────────────────
    public AppraisalDetailDTO addRemarks(Long appraisalId, RemarkRequest req) {
        SelfAppraisal appraisal = appraisalRepo.findById(appraisalId)
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found"));

        String approverName = resolveEmployeeName(req.getApproverId());

        if (req.getOverallRemark() != null && !req.getOverallRemark().isBlank()) {
            AppraisalRemark r = new AppraisalRemark();
            r.setAppraisal(appraisal);
            r.setApproverId(req.getApproverId());
            r.setApproverLevel(req.getApproverLevel());
            r.setRemarkText(req.getOverallRemark());
            remarkRepo.save(r);
        }

        List<SelfAppraisalAnswer> answers = answerRepo.findByAppraisal_Id(appraisalId);
        Map<Long, SelfAppraisalAnswer> answerByQuestionId = answers.stream()
                .filter(a -> a.getQuestion() != null)
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

        if (req.getQuestionRemarks() != null) {
            for (RemarkRequest.QuestionRemarkDTO qr : req.getQuestionRemarks()) {
                AppraisalQuestion q = questionRepo.findById(qr.getQuestionId())
                        .orElseThrow(() -> new EntityNotFoundException("Question not found"));

                boolean isSuggestion = SUGGESTION_SECTION.equalsIgnoreCase(q.getSection());

                AppraisalRemark r = new AppraisalRemark();
                r.setAppraisal(appraisal);
                r.setQuestion(q);
                r.setApproverId(req.getApproverId());
                r.setApproverLevel(req.getApproverLevel());
                r.setRemarkText(qr.getRemarkText());
                if (!isSuggestion) r.setRevisedRating(qr.getRevisedRating());
                remarkRepo.save(r);

                SelfAppraisalAnswer ans = answerByQuestionId.get(qr.getQuestionId());
                if (ans != null) {
                    if (req.getApproverLevel() == AppraisalRemark.ApproverLevel.L1) {
                        if (!isSuggestion) ans.setRevisedRating(qr.getRevisedRating());
                        ans.setRevisedRemarks(qr.getRemarkText());
                    } else {
                        if (!isSuggestion) ans.setFinalRating(qr.getRevisedRating());
                        ans.setFinalRemarks(qr.getRemarkText());
                    }
                    answerRepo.save(ans);
                }
            }
        }

        // Draft-only mode: remarks saved, no status change, no notifications
        if (req.isDraftOnly()) {
            return buildDetailDTO(appraisal, true);
        }

        String prev = appraisal.getStatus().name();
        String cycleLabel = appraisal.getCycle().getCycleLabel();

        if (req.getApproverLevel() == AppraisalRemark.ApproverLevel.L1) {
            if (req.isApprove()) {
                appraisal.setStatus(AppraisalStatus.L1_APPROVED);
                appraisal.setL1ReviewedAt(LocalDateTime.now());
                appraisalRepo.save(appraisal);

                recordHistory(appraisal, prev, AppraisalStatus.L1_APPROVED.name(),
                        req.getApproverId(), approverName,
                        AppraisalStatusHistory.ActionType.L1_APPROVED, req.getOverallRemark());

                if (appraisal.getFinalApproverId() != null) {
                    String empName = resolveEmployeeName(appraisal.getEmployeeId());
                    notificationService.notifyL2PendingReview(
                            appraisal.getFinalApproverId(),
                            appraisal.getEmployeeId(), empName, cycleLabel);
                }
            } else {
                appraisal.setStatus(AppraisalStatus.L1_REJECTED);
                appraisal.setL1ReviewedAt(LocalDateTime.now());
                appraisalRepo.save(appraisal);

                recordHistory(appraisal, prev, AppraisalStatus.L1_REJECTED.name(),
                        req.getApproverId(), approverName,
                        AppraisalStatusHistory.ActionType.L1_REJECTED, req.getOverallRemark());

                notificationService.notifyEmployeeL1Rejected(
                        appraisal.getEmployeeId(), cycleLabel, req.getOverallRemark());
            }
        } else {
            if (req.isPublish()) {
                appraisal.setStatus(AppraisalStatus.PUBLISHED);
                appraisal.setPublishedAt(LocalDateTime.now());
                appraisalRepo.save(appraisal);

                recordHistory(appraisal, prev, AppraisalStatus.PUBLISHED.name(),
                        req.getApproverId(), approverName,
                        AppraisalStatusHistory.ActionType.PUBLISHED, "Published");

                notificationService.notifyEmployeePublished(appraisal.getEmployeeId(), cycleLabel);
            } else if (req.isApprove()) {
                appraisal.setStatus(AppraisalStatus.FINAL_REVIEW);
                appraisalRepo.save(appraisal);

                recordHistory(appraisal, prev, AppraisalStatus.FINAL_REVIEW.name(),
                        req.getApproverId(), approverName,
                        AppraisalStatusHistory.ActionType.L2_APPROVED, req.getOverallRemark());
            } else {
                // L2 reject — send back to L1 for re-review (before publish only)
                appraisal.setStatus(AppraisalStatus.L2_REJECTED);
                appraisalRepo.save(appraisal);

                recordHistory(appraisal, prev, AppraisalStatus.L2_REJECTED.name(),
                        req.getApproverId(), approverName,
                        AppraisalStatusHistory.ActionType.L2_REJECTED, req.getOverallRemark());

                if (appraisal.getFirstApproverId() != null) {
                    String empName = resolveEmployeeName(appraisal.getEmployeeId());
                    notificationService.notifyL1PendingReview(
                            appraisal.getFirstApproverId(),
                            appraisal.getEmployeeId(), empName, cycleLabel);
                }
            }
        }

        return buildDetailDTO(appraisal, true);
    }

    // ── Employee published view ───────────────────────────────────────────────
    public AppraisalDetailDTO getPublished(String employeeId, Long cycleId) {
        SelfAppraisal a = appraisalRepo.findByEmployeeIdAndCycle_Id(employeeId, cycleId)
                .orElseThrow(() -> new EntityNotFoundException("Appraisal not found"));
        boolean show = a.getStatus() == AppraisalStatus.PUBLISHED
                || a.getStatus() == AppraisalStatus.CLOSED;
        return buildDetailDTO(a, show);
    }

    public List<EmployeeAppraisalSummaryDTO> getMyHistory(String employeeId) {
        return appraisalRepo.findByEmployeeId(employeeId)
                .stream().map(this::buildSummary).collect(Collectors.toList());
    }

    public List<EmployeeAppraisalSummaryDTO> getAllAppraisals(Long cycleId) {
        List<SelfAppraisal> all = (cycleId != null)
                ? appraisalRepo.findByCycle_Id(cycleId)
                : appraisalRepo.findAll();
        return all.stream().map(this::buildSummary).collect(Collectors.toList());
    }

    // ── Excel Export (Admin) ──────────────────────────────────────────────────
    public void exportToExcel(Long cycleId, String statusFilter, HttpServletResponse response) throws IOException {
        List<SelfAppraisal> all = (cycleId != null)
                ? appraisalRepo.findByCycle_Id(cycleId)
                : appraisalRepo.findAll();
        List<SelfAppraisal> appraisals = filterByStatus(all, statusFilter);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"appraisal_export_"
                        + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx\"");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Appraisals");

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            String[] headers = {
                    "Employee ID", "Employee Name", "Department", "Role",
                    "Experience Type", "Company Experience",
                    "Cycle", "Status", "Employee Submit Date/Time",
                    "L1 Approver ID", "L1 Approver Name", "L1 Review Date/Time",
                    "L1 Overall Remark", "L1 Overall Rating",
                    "L2 Approver ID", "L2 Approver Name", "L2 Publish Date/Time",
                    "L2 Overall Remark", "L2 Overall Rating",
                    "Section", "Question",
                    "Self Answer", "Self Rating",
                    "L1 Revised Rating", "L1 Remarks",
                    "L2 Final Rating", "L2 Remarks",
                    "Overall Avg Rating (excl. Suggestions)"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;

            for (SelfAppraisal appraisal : appraisals) {
                Employee emp = employeeRepo.findByEmpId(appraisal.getEmployeeId()).orElse(null);
                String empName    = emp != null ? emp.getName()  : appraisal.getEmployeeId();
                String dept       = emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getDepartmentName() : "";
                String role       = emp != null && emp.getRole() != null
                        ? emp.getRole().getRoleName() : "";

                String expType    = "Experienced";
                String companyExp = "N/A";
                if (emp != null && emp.getOnboarding() != null
                        && emp.getOnboarding().getJoiningDate() != null) {
                    LocalDate doj = emp.getOnboarding().getJoiningDate();
                    Period p      = Period.between(doj, LocalDate.now());
                    companyExp    = p.getYears() + " yr " + p.getMonths() + " mo";
                    expType       = (p.getYears() == 0 && p.getMonths() < 6) ? "Fresher" : "Experienced";
                }

                String l1Name = resolveEmployeeName(appraisal.getFirstApproverId());
                String l2Name = resolveEmployeeName(appraisal.getFinalApproverId());

                List<AppraisalRemark> allRemarks = remarkRepo.findByAppraisal_Id(appraisal.getId());

                String l1OverallRemark = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRemarkText).findFirst().orElse("");
                Integer l1OverallRating = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRevisedRating).findFirst().orElse(null);

                String l2OverallRemark = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRemarkText).findFirst().orElse("");
                Integer l2OverallRating = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRevisedRating).findFirst().orElse(null);

                // FIX: build per-question remark lookup maps (were missing from original)
                Map<Long, AppraisalRemark> l1Map = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() != null)
                        .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));
                Map<Long, AppraisalRemark> l2Map = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() != null)
                        .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));

                List<AppraisalQuestion> questions = sortedBySection(questionRepo
                        .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(appraisal.getCycle().getId()));
                List<SelfAppraisalAnswer> answers = answerRepo.findByAppraisal_Id(appraisal.getId());
                Map<Long, SelfAppraisalAnswer> ansMap = answers.stream()
                        .filter(a -> a.getQuestion() != null)
                        .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

                List<Integer> ratedRatings = answers.stream()
                        .filter(a -> a.getSelfRating() != null)
                        .filter(a -> !SUGGESTION_SECTION.equalsIgnoreCase(a.getQuestion().getSection()))
                        .map(SelfAppraisalAnswer::getSelfRating)
                        .collect(Collectors.toList());
                double overallAvg = ratedRatings.isEmpty() ? 0.0
                        : ratedRatings.stream().mapToInt(i -> i).average().orElse(0.0);
                String overallAvgStr = overallAvg > 0
                        ? String.valueOf(Math.round(overallAvg * 100.0) / 100.0) : "";

                // FIX: write one row per question (original had broken loop referencing undefined vars)
                for (AppraisalQuestion q : questions) {
                    SelfAppraisalAnswer ans = ansMap.get(q.getId());
                    AppraisalRemark l1r     = l1Map.get(q.getId());
                    AppraisalRemark l2r     = l2Map.get(q.getId());
                    boolean isSuggestion    = SUGGESTION_SECTION.equalsIgnoreCase(q.getSection());

                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(appraisal.getEmployeeId());
                    row.createCell(1).setCellValue(empName);
                    row.createCell(2).setCellValue(dept);
                    row.createCell(3).setCellValue(role);
                    row.createCell(4).setCellValue(expType);
                    row.createCell(5).setCellValue(companyExp);
                    row.createCell(6).setCellValue(appraisal.getCycle().getCycleLabel());
                    row.createCell(7).setCellValue(appraisal.getStatus().name());
                    row.createCell(8).setCellValue(
                            appraisal.getSubmittedAt() != null ? appraisal.getSubmittedAt().format(dtf) : "");
                    row.createCell(9).setCellValue(nvl(appraisal.getFirstApproverId()));
                    row.createCell(10).setCellValue(nvl(l1Name));
                    row.createCell(11).setCellValue(
                            appraisal.getL1ReviewedAt() != null ? appraisal.getL1ReviewedAt().format(dtf) : "");
                    row.createCell(12).setCellValue(l1OverallRemark);
                    if (l1OverallRating != null) row.createCell(13).setCellValue(l1OverallRating);
                    row.createCell(14).setCellValue(nvl(appraisal.getFinalApproverId()));
                    row.createCell(15).setCellValue(nvl(l2Name));
                    row.createCell(16).setCellValue(
                            appraisal.getPublishedAt() != null ? appraisal.getPublishedAt().format(dtf) : "");
                    row.createCell(17).setCellValue(l2OverallRemark);
                    if (l2OverallRating != null) row.createCell(18).setCellValue(l2OverallRating);

                    row.createCell(19).setCellValue(q.getSection());
                    row.createCell(20).setCellValue(q.getQuestionText());

                    // Self answer — append project list for project question
                    String selfAnswer = (ans != null && ans.getAnswerText() != null) ? ans.getAnswerText() : "";
                    if (q.getQuestionText() != null && q.getQuestionText().toLowerCase().contains("project")) {
                        List<AppraisalProject> projs =
                                projectRepo.findByAppraisal_IdAndQuestion_Id(appraisal.getId(), q.getId());
                        if (!projs.isEmpty()) {
                            StringBuilder sb = new StringBuilder(selfAnswer.isEmpty() ? "" : selfAnswer + "\n\n");
                            sb.append("Projects:\n");
                            for (int pi = 0; pi < projs.size(); pi++) {
                                sb.append(String.format("%02d. %s\n    %s\n",
                                        pi + 1, projs.get(pi).getProjectName(),
                                        projs.get(pi).getDescription() != null
                                                ? projs.get(pi).getDescription() : ""));
                            }
                            selfAnswer = sb.toString().trim();
                        }
                    }
                    row.createCell(21).setCellValue(selfAnswer);

                    if (!isSuggestion && ans != null && ans.getSelfRating() != null)
                        row.createCell(22).setCellValue(ans.getSelfRating());

                    // L1 revised rating — answer field first, fallback to remark table
                    Integer l1Rating = (ans != null && ans.getRevisedRating() != null)
                            ? ans.getRevisedRating()
                            : (l1r != null ? l1r.getRevisedRating() : null);
                    if (!isSuggestion && l1Rating != null)
                        row.createCell(23).setCellValue(l1Rating);

                    String l1RemarkText = (ans != null && ans.getRevisedRemarks() != null)
                            ? ans.getRevisedRemarks()
                            : (l1r != null ? l1r.getRemarkText() : null);
                    if (l1RemarkText != null)
                        row.createCell(24).setCellValue(l1RemarkText);

                    // L2 final rating — answer field first, fallback to remark table
                    Integer l2Rating = (ans != null && ans.getFinalRating() != null)
                            ? ans.getFinalRating()
                            : (l2r != null ? l2r.getRevisedRating() : null);
                    if (!isSuggestion && l2Rating != null)
                        row.createCell(25).setCellValue(l2Rating);

                    String l2RemarkText = (ans != null && ans.getFinalRemarks() != null)
                            ? ans.getFinalRemarks()
                            : (l2r != null ? l2r.getRemarkText() : null);
                    if (l2RemarkText != null)
                        row.createCell(26).setCellValue(l2RemarkText);

                    row.createCell(27).setCellValue(overallAvgStr);
                }
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(response.getOutputStream());
        }
    }

    // ── PDF Export (Admin) ────────────────────────────────────────────────────
    public void exportToPdf(Long cycleId, String statusFilter, HttpServletResponse response) throws IOException {
        List<SelfAppraisal> all = (cycleId != null)
                ? appraisalRepo.findByCycle_Id(cycleId)
                : appraisalRepo.findAll();
        List<SelfAppraisal> appraisals = filterByStatus(all, statusFilter);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"appraisal_export_"
                        + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf\"");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        Document doc = new Document(
                PageSize.A4, 40, 40, 50, 50);

        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            Font titleFont    = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font sectionFont  = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font labelFont    = new Font(Font.HELVETICA, 9,  Font.BOLD);
            Font normalFont   = new Font(Font.HELVETICA, 9,  Font.NORMAL);
            Font smallFont    = new Font(Font.HELVETICA, 8,  Font.ITALIC);
            Font remarkL1Font = new Font(Font.HELVETICA, 8,  Font.NORMAL,
                    new java.awt.Color(0, 102, 102));
            Font remarkL2Font = new Font(Font.HELVETICA, 8,  Font.NORMAL,
                    new java.awt.Color(80, 0, 120));

            boolean firstAppraisal = true;

            for (SelfAppraisal appraisal : appraisals) {
                if (!firstAppraisal) doc.newPage();
                firstAppraisal = false;

                Employee emp = employeeRepo.findByEmpId(appraisal.getEmployeeId()).orElse(null);
                String empName    = emp != null ? emp.getName() : appraisal.getEmployeeId();
                String dept       = emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getDepartmentName() : "—";
                String role       = emp != null && emp.getRole() != null
                        ? emp.getRole().getRoleName() : "—";

                String expType    = "Experienced";
                String companyExp = "N/A";
                if (emp != null && emp.getOnboarding() != null
                        && emp.getOnboarding().getJoiningDate() != null) {
                    LocalDate doj = emp.getOnboarding().getJoiningDate();
                    Period p = Period.between(doj, LocalDate.now());
                    companyExp = p.getYears() + " yr " + p.getMonths() + " mo";
                    expType = (p.getYears() == 0 && p.getMonths() < 6) ? "Fresher" : "Experienced";
                }

                String l1Name = resolveEmployeeName(appraisal.getFirstApproverId());
                String l2Name = resolveEmployeeName(appraisal.getFinalApproverId());

                List<AppraisalRemark> allRemarks = remarkRepo.findByAppraisal_Id(appraisal.getId());

                String l1OverallRemark = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRemarkText).findFirst().orElse("—");
                String l2OverallRemark = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() == null)
                        .map(AppraisalRemark::getRemarkText).findFirst().orElse("—");

                Map<Long, AppraisalRemark> l1RemarkMap = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() != null)
                        .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));
                Map<Long, AppraisalRemark> l2RemarkMap = allRemarks.stream()
                        .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() != null)
                        .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));

                List<AppraisalQuestion>   questions = sortedBySection(questionRepo
                        .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(appraisal.getCycle().getId()));
                List<SelfAppraisalAnswer> answers   = answerRepo.findByAppraisal_Id(appraisal.getId());
                Map<Long, SelfAppraisalAnswer> ansMap = answers.stream()
                        .filter(a -> a.getQuestion() != null)
                        .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

                List<Integer> ratedRatings = answers.stream()
                        .filter(a -> a.getSelfRating() != null)
                        .filter(a -> !SUGGESTION_SECTION.equalsIgnoreCase(a.getQuestion().getSection()))
                        .map(SelfAppraisalAnswer::getSelfRating).collect(Collectors.toList());
                double overallAvg = ratedRatings.isEmpty() ? 0.0
                        : ratedRatings.stream().mapToInt(i -> i).average().orElse(0.0);

                // ── Page Title ───────────────────────────────────────────────
                Paragraph title = new Paragraph("SELF APPRAISAL REPORT", titleFont);
                title.setAlignment(Paragraph.ALIGN_CENTER);
                title.setSpacingAfter(2);
                doc.add(title);

                Paragraph subTitle = new Paragraph(empName + "  |  " + appraisal.getCycle().getCycleLabel(), sectionFont);
                subTitle.setAlignment(Paragraph.ALIGN_CENTER);
                subTitle.setSpacingAfter(10);
                doc.add(subTitle);

                // ── Employee Info (4 col: label | value | label | value) ─────
                PdfPTable infoTable = new PdfPTable(new float[]{2f, 4f, 2f, 4f});
                infoTable.setWidthPercentage(100);
                infoTable.setSpacingAfter(6);
                addInfoCell(infoTable, "Employee ID",  appraisal.getEmployeeId(),                      labelFont, normalFont);
                addInfoCell(infoTable, "Name",         empName,                                         labelFont, normalFont);
                addInfoCell(infoTable, "Department",   dept,                                            labelFont, normalFont);
                addInfoCell(infoTable, "Role",         role,                                            labelFont, normalFont);
                addInfoCell(infoTable, "Experience",   expType + " · " + companyExp,                   labelFont, normalFont);
                addInfoCell(infoTable, "Status",       appraisal.getStatus().name().replace("_", " "), labelFont, normalFont);
                addInfoCell(infoTable, "Submit Date",
                        appraisal.getSubmittedAt() != null ? appraisal.getSubmittedAt().format(dtf) : "—",
                        labelFont, normalFont);
                addInfoCell(infoTable, "Publish Date",
                        appraisal.getPublishedAt() != null ? appraisal.getPublishedAt().format(dtf) : "—",
                        labelFont, normalFont);
                doc.add(infoTable);

                // ── Approver Info (2 col) ────────────────────────────────────
                PdfPTable approverTable = new PdfPTable(new float[]{1f, 1f});
                approverTable.setWidthPercentage(100);
                approverTable.setSpacingAfter(6);

                // L1
                PdfPCell l1HeaderCell = new PdfPCell();
                l1HeaderCell.setPadding(6);
                l1HeaderCell.setBackgroundColor(new java.awt.Color(220, 245, 240));
                l1HeaderCell.addElement(new Phrase("L1 Manager (First Approver)", labelFont));
                l1HeaderCell.addElement(new Phrase("ID: " + nvl(appraisal.getFirstApproverId()) + "   Name: " + nvl(l1Name), normalFont));
                l1HeaderCell.addElement(new Phrase("Overall Remark: " + l1OverallRemark, remarkL1Font));
                if (appraisal.getL1ReviewedAt() != null)
                    l1HeaderCell.addElement(new Phrase("Reviewed: " + appraisal.getL1ReviewedAt().format(dtf), smallFont));
                approverTable.addCell(l1HeaderCell);

                // L2
                PdfPCell l2HeaderCell = new PdfPCell();
                l2HeaderCell.setPadding(6);
                l2HeaderCell.setBackgroundColor(new java.awt.Color(240, 225, 255));
                l2HeaderCell.addElement(new Phrase("L2 Final Approver", labelFont));
                l2HeaderCell.addElement(new Phrase("ID: " + nvl(appraisal.getFinalApproverId()) + "   Name: " + nvl(l2Name), normalFont));
                l2HeaderCell.addElement(new Phrase("Overall Remark: " + l2OverallRemark, remarkL2Font));
                if (appraisal.getPublishedAt() != null)
                    l2HeaderCell.addElement(new Phrase("Published: " + appraisal.getPublishedAt().format(dtf), smallFont));
                approverTable.addCell(l2HeaderCell);
                doc.add(approverTable);

                // ── Overall Avg Rating ───────────────────────────────────────
                if (overallAvg > 0) {
                    double rounded = Math.round(overallAvg * 100.0) / 100.0;
                    PdfPTable avgTable = new PdfPTable(1);
                    avgTable.setWidthPercentage(100);
                    avgTable.setSpacingAfter(8);
                    PdfPCell avgCell = new PdfPCell(new Phrase(
                            "Overall Average Rating (excl. Suggestions): " + rounded + " / 5.0", sectionFont));
                    avgCell.setPadding(6);
                    avgCell.setBackgroundColor(new java.awt.Color(255, 250, 220));
                    avgCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    avgTable.addCell(avgCell);
                    doc.add(avgTable);
                }

                // ── Column Header Row for Questions ──────────────────────────
                PdfPTable colHeaderTable = new PdfPTable(new float[]{3f, 22f, 14f, 6f, 14f, 6f, 14f, 6f});
                colHeaderTable.setWidthPercentage(100);
                colHeaderTable.setSpacingAfter(2);
                java.awt.Color colHeaderBg = new java.awt.Color(50, 80, 160);
                Font colHeaderFont = new Font(Font.HELVETICA, 8, Font.BOLD, java.awt.Color.WHITE);
                for (String h : new String[]{"#", "Question", "Self Answer", "Self\nRating",
                        "L1 Remark", "L1\nRating", "L2 Remark", "L2\nRating"}) {
                    PdfPCell ch = new PdfPCell(new Phrase(h, colHeaderFont));
                    ch.setBackgroundColor(colHeaderBg);
                    ch.setPadding(5);
                    ch.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    ch.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    colHeaderTable.addCell(ch);
                }
                doc.add(colHeaderTable);

                // ── Questions grouped by section ─────────────────────────────
                String currentSection = null;
                int qNum = 1;
                java.awt.Color rowEven = new java.awt.Color(248, 249, 255);
                java.awt.Color rowOdd  = java.awt.Color.WHITE;
                java.awt.Color suggBg  = new java.awt.Color(255, 252, 235);
                int globalQNum = 0;

                for (AppraisalQuestion q : questions) {
                    boolean isSuggestion = SUGGESTION_SECTION.equalsIgnoreCase(q.getSection());

                    // Section header — printed only once per section
                    if (!q.getSection().equals(currentSection)) {
                        currentSection = q.getSection();
                        qNum = 1;

                        PdfPTable secTable = new PdfPTable(1);
                        secTable.setWidthPercentage(100);
                        secTable.setSpacingBefore(6);
                        secTable.setSpacingAfter(2);
                        PdfPCell secCell = new PdfPCell(new Phrase(
                                currentSection + (isSuggestion ? "  (Optional / No Rating)" : ""), sectionFont));
                        secCell.setPadding(5);
                        secCell.setBackgroundColor(isSuggestion
                                ? new java.awt.Color(255, 245, 200)
                                : new java.awt.Color(210, 220, 245));
                        secCell.setBorderColor(new java.awt.Color(150, 170, 220));
                        secTable.addCell(secCell);
                        doc.add(secTable);
                    }

                    SelfAppraisalAnswer ans   = ansMap.get(q.getId());
                    AppraisalRemark     l1r   = l1RemarkMap.get(q.getId());
                    AppraisalRemark     l2r   = l2RemarkMap.get(q.getId());
                    globalQNum++;
                    java.awt.Color rowBg = (globalQNum % 2 == 0) ? rowEven : rowOdd;

                    if (isSuggestion) {
                        // Suggestion: 2 columns only — # | question + answer (spans rest)
                        PdfPTable suggTable = new PdfPTable(new float[]{3f, 62f});
                        suggTable.setWidthPercentage(100);
                        suggTable.setSpacingAfter(2);

                        PdfPCell sNum = new PdfPCell(new Phrase(String.format("%02d", qNum), labelFont));
                        sNum.setPadding(4); sNum.setBackgroundColor(suggBg);
                        suggTable.addCell(sNum);

                        String suggText = q.getQuestionText() + (q.isRequired() ? "" : "  (optional)");
                        String suggAns  = (ans != null && ans.getAnswerText() != null) ? ans.getAnswerText() : "—";
                        PdfPCell sCell  = new PdfPCell();
                        sCell.setPadding(4); sCell.setBackgroundColor(suggBg);
                        sCell.addElement(new Phrase(suggText, normalFont));
                        sCell.addElement(new Phrase("Answer: " + suggAns, smallFont));
                        suggTable.addCell(sCell);
                        doc.add(suggTable);

                    } else {
                        // Rated question: 8 columns
                        PdfPTable qTable = new PdfPTable(new float[]{3f, 22f, 14f, 6f, 14f, 6f, 14f, 6f});
                        qTable.setWidthPercentage(100);
                        qTable.setSpacingAfter(2);

                        // # column
                        PdfPCell numCell = new PdfPCell(new Phrase(String.format("%02d", qNum), labelFont));
                        numCell.setPadding(4);
                        numCell.setBackgroundColor(new java.awt.Color(220, 225, 250));
                        numCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                        numCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                        qTable.addCell(numCell);

                        // Question text
                        PdfPCell qCell = new PdfPCell(new Phrase(q.getQuestionText(), normalFont));
                        qCell.setPadding(4); qCell.setBackgroundColor(rowBg);
                        qTable.addCell(qCell);

                        // Self answer
                        String selfAnswer = (ans != null && ans.getAnswerText() != null) ? ans.getAnswerText() : "—";

                        // Append project list for project question
                        if (q.getQuestionText() != null && q.getQuestionText().toLowerCase().contains("project")) {
                            List<AppraisalProject> projs = projectRepo.findByAppraisal_IdAndQuestion_Id(appraisal.getId(), q.getId());
                            if (!projs.isEmpty()) {
                                StringBuilder sb = new StringBuilder(selfAnswer.equals("—") ? "" : selfAnswer + "\n\n");
                                sb.append("Projects:\n");
                                for (int pi = 0; pi < projs.size(); pi++) {
                                    sb.append(String.format("%02d. %s\n    %s\n",
                                            pi + 1, projs.get(pi).getProjectName(),
                                            projs.get(pi).getDescription() != null ? projs.get(pi).getDescription() : ""));
                                }
                                selfAnswer = sb.toString().trim();
                            }
                        }
                        PdfPCell selfCell = new PdfPCell(new Phrase(selfAnswer, normalFont));
                        selfCell.setPadding(4); selfCell.setBackgroundColor(rowBg);
                        qTable.addCell(selfCell);

                        // Self rating
                        String selfRatingStr = (ans != null && ans.getSelfRating() != null)
                                ? ans.getSelfRating() + "/5" : "—";
                        PdfPCell selfRatingCell = new PdfPCell(new Phrase(selfRatingStr, labelFont));
                        selfRatingCell.setPadding(4); selfRatingCell.setBackgroundColor(rowBg);
                        selfRatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                        selfRatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                        qTable.addCell(selfRatingCell);

                        // L1 remark
                        String l1RemarkStr = (ans != null && ans.getRevisedRemarks() != null)
                                ? ans.getRevisedRemarks()
                                : (l1r != null && l1r.getRemarkText() != null ? l1r.getRemarkText() : "—");
                        PdfPCell l1RCell = new PdfPCell(new Phrase(l1RemarkStr, remarkL1Font));
                        l1RCell.setPadding(4);
                        l1RCell.setBackgroundColor(new java.awt.Color(235, 252, 248));
                        qTable.addCell(l1RCell);

                        // L1 rating
                        Integer l1Rating = (ans != null && ans.getRevisedRating() != null)
                                ? ans.getRevisedRating()
                                : (l1r != null ? l1r.getRevisedRating() : null);
                        String l1RatingStr = l1Rating != null ? l1Rating + "/5" : "—";
                        PdfPCell l1RatingCell = new PdfPCell(new Phrase(l1RatingStr, remarkL1Font));
                        l1RatingCell.setPadding(4);
                        l1RatingCell.setBackgroundColor(new java.awt.Color(235, 252, 248));
                        l1RatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                        l1RatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                        qTable.addCell(l1RatingCell);

                        // L2 remark
                        String l2RemarkStr = (ans != null && ans.getFinalRemarks() != null)
                                ? ans.getFinalRemarks()
                                : (l2r != null && l2r.getRemarkText() != null ? l2r.getRemarkText() : "—");
                        PdfPCell l2RCell = new PdfPCell(new Phrase(l2RemarkStr, remarkL2Font));
                        l2RCell.setPadding(4);
                        l2RCell.setBackgroundColor(new java.awt.Color(248, 238, 255));
                        qTable.addCell(l2RCell);

                        // L2 rating
                        Integer l2Rating = (ans != null && ans.getFinalRating() != null)
                                ? ans.getFinalRating()
                                : (l2r != null ? l2r.getRevisedRating() : null);
                        String l2RatingStr = l2Rating != null ? l2Rating + "/5" : "—";
                        PdfPCell l2RatingCell = new PdfPCell(new Phrase(l2RatingStr, remarkL2Font));
                        l2RatingCell.setPadding(4);
                        l2RatingCell.setBackgroundColor(new java.awt.Color(248, 238, 255));
                        l2RatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                        l2RatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                        qTable.addCell(l2RatingCell);

                        doc.add(qTable);
                    }
                    qNum++;
                }
            }

            doc.close();

        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    // ── Employee: export their own published appraisal as PDF ────────────────
    public void exportEmployeePdf(String employeeId, Long cycleId, HttpServletResponse response)
            throws IOException {

        SelfAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndCycle_Id(employeeId, cycleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appraisal not found"));

        if (appraisal.getStatus() != AppraisalStatus.PUBLISHED
                && appraisal.getStatus() != AppraisalStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appraisal is not yet published");
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"my_appraisal_"
                        + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf\"");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        Document doc = new Document(
                PageSize.A4, 40, 40, 50, 50);

        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            Font titleFont    = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font sectionFont  = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font labelFont    = new Font(Font.HELVETICA, 9,  Font.BOLD);
            Font normalFont   = new Font(Font.HELVETICA, 9,  Font.NORMAL);
            Font remarkL1Font = new Font(Font.HELVETICA, 8,  Font.NORMAL,
                    new java.awt.Color(0, 102, 102));
            Font remarkL2Font = new Font(Font.HELVETICA, 8,  Font.NORMAL,
                    new java.awt.Color(80, 0, 120));

            Employee emp = employeeRepo.findByEmpId(appraisal.getEmployeeId()).orElse(null);
            String empName = emp != null ? emp.getName() : appraisal.getEmployeeId();
            String dept    = emp != null && emp.getDepartment() != null
                    ? emp.getDepartment().getDepartmentName() : "—";
            String role    = emp != null && emp.getRole() != null
                    ? emp.getRole().getRoleName() : "—";

            // ── Page Title ───────────────────────────────────────────────────
            Paragraph title = new Paragraph("MY APPRAISAL REPORT", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            title.setSpacingAfter(2);
            doc.add(title);

            Paragraph subTitle = new Paragraph(
                    empName + "  |  " + (appraisal.getCycle() != null ? appraisal.getCycle().getCycleLabel() : ""), sectionFont);
            subTitle.setAlignment(Paragraph.ALIGN_CENTER);
            subTitle.setSpacingAfter(10);
            doc.add(subTitle);

            // ── Employee Info (4 col) ────────────────────────────────────────
            PdfPTable infoTable = new PdfPTable(new float[]{2f, 4f, 2f, 4f});
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(6);
            addInfoCell(infoTable, "Employee ID",  appraisal.getEmployeeId(),                      labelFont, normalFont);
            addInfoCell(infoTable, "Name",         empName,                                         labelFont, normalFont);
            addInfoCell(infoTable, "Department",   dept,                                            labelFont, normalFont);
            addInfoCell(infoTable, "Role",         role,                                            labelFont, normalFont);
            addInfoCell(infoTable, "Cycle",
                    appraisal.getCycle() != null ? appraisal.getCycle().getCycleLabel() : "—",     labelFont, normalFont);
            addInfoCell(infoTable, "Status",
                    appraisal.getStatus().name().replace("_", " "),                                labelFont, normalFont);
            addInfoCell(infoTable, "Submit Date",
                    appraisal.getSubmittedAt() != null ? appraisal.getSubmittedAt().format(dtf) : "—",
                    labelFont, normalFont);
            addInfoCell(infoTable, "Publish Date",
                    appraisal.getPublishedAt() != null ? appraisal.getPublishedAt().format(dtf) : "—",
                    labelFont, normalFont);
            doc.add(infoTable);

            // ── Compute data ─────────────────────────────────────────────────
            List<SelfAppraisalAnswer> answers = answerRepo.findByAppraisal_Id(appraisal.getId());
            List<Integer> ratedRatings = answers.stream()
                    .filter(a -> a.getSelfRating() != null)
                    .filter(a -> !SUGGESTION_SECTION.equalsIgnoreCase(a.getQuestion().getSection()))
                    .map(SelfAppraisalAnswer::getSelfRating).collect(Collectors.toList());
            double overallAvg = ratedRatings.isEmpty() ? 0.0
                    : ratedRatings.stream().mapToInt(i -> i).average().orElse(0.0);

            List<AppraisalRemark> allRemarks = remarkRepo.findByAppraisal_Id(appraisal.getId());
            String l1Name    = resolveEmployeeName(appraisal.getFirstApproverId());
            String l2Name    = resolveEmployeeName(appraisal.getFinalApproverId());
            String l1Overall = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() == null)
                    .map(AppraisalRemark::getRemarkText).findFirst().orElse("—");
            String l2Overall = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() == null)
                    .map(AppraisalRemark::getRemarkText).findFirst().orElse("—");

            // ── Approver Info ────────────────────────────────────────────────
            Font smallFont = new Font(Font.HELVETICA, 8, Font.ITALIC);
            PdfPTable approverTable = new PdfPTable(new float[]{1f, 1f});
            approverTable.setWidthPercentage(100);
            approverTable.setSpacingAfter(6);

            PdfPCell l1HeaderCell = new PdfPCell();
            l1HeaderCell.setPadding(6);
            l1HeaderCell.setBackgroundColor(new java.awt.Color(220, 245, 240));
            l1HeaderCell.addElement(new Phrase("L1 Manager (First Approver)", labelFont));
            l1HeaderCell.addElement(new Phrase("ID: " + nvl(appraisal.getFirstApproverId()) + "   Name: " + nvl(l1Name), normalFont));
            l1HeaderCell.addElement(new Phrase("Overall Remark: " + l1Overall, remarkL1Font));
            if (appraisal.getL1ReviewedAt() != null)
                l1HeaderCell.addElement(new Phrase("Reviewed: " + appraisal.getL1ReviewedAt().format(dtf), smallFont));
            approverTable.addCell(l1HeaderCell);

            PdfPCell l2HeaderCell = new PdfPCell();
            l2HeaderCell.setPadding(6);
            l2HeaderCell.setBackgroundColor(new java.awt.Color(240, 225, 255));
            l2HeaderCell.addElement(new Phrase("L2 Final Approver", labelFont));
            l2HeaderCell.addElement(new Phrase("ID: " + nvl(appraisal.getFinalApproverId()) + "   Name: " + nvl(l2Name), normalFont));
            l2HeaderCell.addElement(new Phrase("Overall Remark: " + l2Overall, remarkL2Font));
            if (appraisal.getPublishedAt() != null)
                l2HeaderCell.addElement(new Phrase("Published: " + appraisal.getPublishedAt().format(dtf), smallFont));
            approverTable.addCell(l2HeaderCell);
            doc.add(approverTable);

            // ── Overall Avg ──────────────────────────────────────────────────
            if (overallAvg > 0) {
                PdfPTable avgTable = new PdfPTable(1);
                avgTable.setWidthPercentage(100);
                avgTable.setSpacingAfter(8);
                PdfPCell avgCell = new PdfPCell(new Phrase(
                        "Overall Average Rating (excl. Suggestions): "
                                + Math.round(overallAvg * 100.0) / 100.0 + " / 5.0", sectionFont));
                avgCell.setPadding(6);
                avgCell.setBackgroundColor(new java.awt.Color(255, 250, 220));
                avgCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                avgTable.addCell(avgCell);
                doc.add(avgTable);
            }

            // ── Column Header ────────────────────────────────────────────────
            PdfPTable colHeaderTable = new PdfPTable(new float[]{3f, 22f, 14f, 6f, 14f, 6f, 14f, 6f});
            colHeaderTable.setWidthPercentage(100);
            colHeaderTable.setSpacingAfter(2);
            java.awt.Color colHeaderBg = new java.awt.Color(50, 80, 160);
            Font colHeaderFont = new Font(Font.HELVETICA, 8, Font.BOLD, java.awt.Color.WHITE);
            for (String h : new String[]{"#", "Question", "Your Answer", "Self\nRating",
                    "L1 Remark", "L1\nRating", "L2 Remark", "L2\nRating"}) {
                PdfPCell ch = new PdfPCell(new Phrase(h, colHeaderFont));
                ch.setBackgroundColor(colHeaderBg);
                ch.setPadding(5);
                ch.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                ch.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                colHeaderTable.addCell(ch);
            }
            doc.add(colHeaderTable);

            // ── Build per-question remark lookups ────────────────────────────
            Map<Long, AppraisalRemark> l1RemarkMap = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() != null)
                    .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));
            Map<Long, AppraisalRemark> l2RemarkMap = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() != null)
                    .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));

            List<AppraisalQuestion> questions = sortedBySection(questionRepo
                    .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(appraisal.getCycle().getId()));
            Map<Long, SelfAppraisalAnswer> ansMap = answers.stream()
                    .filter(a -> a.getQuestion() != null)
                    .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

            String currentSection = null;
            int qNum = 1;
            int globalQNum = 0;
            java.awt.Color rowEven = new java.awt.Color(248, 249, 255);
            java.awt.Color suggBg  = new java.awt.Color(255, 252, 235);

            for (AppraisalQuestion q : questions) {
                String secName = q.getSection() != null ? q.getSection() : "General";
                boolean isSuggestion = SUGGESTION_SECTION.equalsIgnoreCase(secName);

                // Section header — once per section
                if (!secName.equals(currentSection)) {
                    currentSection = secName;
                    qNum = 1;
                    PdfPTable secTable = new PdfPTable(1);
                    secTable.setWidthPercentage(100);
                    secTable.setSpacingBefore(6);
                    secTable.setSpacingAfter(2);
                    PdfPCell secCell = new PdfPCell(new Phrase(
                            secName + (isSuggestion ? "  (Optional / No Rating)" : ""), sectionFont));
                    secCell.setPadding(5);
                    secCell.setBackgroundColor(isSuggestion
                            ? new java.awt.Color(255, 245, 200)
                            : new java.awt.Color(210, 220, 245));
                    secTable.addCell(secCell);
                    doc.add(secTable);
                }

                SelfAppraisalAnswer ans = ansMap.get(q.getId());
                AppraisalRemark l1r = l1RemarkMap.get(q.getId());
                AppraisalRemark l2r = l2RemarkMap.get(q.getId());
                globalQNum++;
                java.awt.Color rowBg = (globalQNum % 2 == 0) ? rowEven : java.awt.Color.WHITE;

                if (isSuggestion) {
                    PdfPTable suggTable = new PdfPTable(new float[]{3f, 62f});
                    suggTable.setWidthPercentage(100);
                    suggTable.setSpacingAfter(2);
                    PdfPCell sNum = new PdfPCell(new Phrase(String.format("%02d", qNum), labelFont));
                    sNum.setPadding(4); sNum.setBackgroundColor(suggBg);
                    suggTable.addCell(sNum);
                    String suggAns = (ans != null && ans.getAnswerText() != null) ? ans.getAnswerText() : "—";
                    PdfPCell sCell = new PdfPCell();
                    sCell.setPadding(4); sCell.setBackgroundColor(suggBg);
                    sCell.addElement(new Phrase(q.getQuestionText(), normalFont));
                    sCell.addElement(new Phrase("Answer: " + suggAns, smallFont));
                    suggTable.addCell(sCell);
                    doc.add(suggTable);
                } else {
                    PdfPTable qTable = new PdfPTable(new float[]{3f, 22f, 14f, 6f, 14f, 6f, 14f, 6f});
                    qTable.setWidthPercentage(100);
                    qTable.setSpacingAfter(2);

                    PdfPCell numCell = new PdfPCell(new Phrase(String.format("%02d", qNum), labelFont));
                    numCell.setPadding(4);
                    numCell.setBackgroundColor(new java.awt.Color(220, 225, 250));
                    numCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    numCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    qTable.addCell(numCell);

                    PdfPCell qCell = new PdfPCell(new Phrase(q.getQuestionText(), normalFont));
                    qCell.setPadding(4); qCell.setBackgroundColor(rowBg);
                    qTable.addCell(qCell);

                    String selfAnswer = (ans != null && ans.getAnswerText() != null) ? ans.getAnswerText() : "—";

                    // Append project list for project question
                    if (q.getQuestionText() != null && q.getQuestionText().toLowerCase().contains("project")) {
                        List<AppraisalProject> projs = projectRepo.findByAppraisal_IdAndQuestion_Id(appraisal.getId(), q.getId());
                        if (!projs.isEmpty()) {
                            StringBuilder sb = new StringBuilder(selfAnswer.equals("—") ? "" : selfAnswer + "\n\n");
                            sb.append("Projects:\n");
                            for (int pi = 0; pi < projs.size(); pi++) {
                                sb.append(String.format("%02d. %s\n    %s\n",
                                        pi + 1, projs.get(pi).getProjectName(),
                                        projs.get(pi).getDescription() != null ? projs.get(pi).getDescription() : ""));
                            }
                            selfAnswer = sb.toString().trim();
                        }
                    }
                    PdfPCell selfCell = new PdfPCell(new Phrase(selfAnswer, normalFont));
                    selfCell.setPadding(4); selfCell.setBackgroundColor(rowBg);
                    qTable.addCell(selfCell);

                    String selfRatingStr = (ans != null && ans.getSelfRating() != null)
                            ? ans.getSelfRating() + "/5" : "—";
                    PdfPCell selfRatingCell = new PdfPCell(new Phrase(selfRatingStr, labelFont));
                    selfRatingCell.setPadding(4); selfRatingCell.setBackgroundColor(rowBg);
                    selfRatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    selfRatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    qTable.addCell(selfRatingCell);

                    String l1RemarkText = (ans != null && ans.getRevisedRemarks() != null)
                            ? ans.getRevisedRemarks()
                            : (l1r != null && l1r.getRemarkText() != null ? l1r.getRemarkText() : "—");
                    PdfPCell l1RCell = new PdfPCell(new Phrase(l1RemarkText, remarkL1Font));
                    l1RCell.setPadding(4);
                    l1RCell.setBackgroundColor(new java.awt.Color(235, 252, 248));
                    qTable.addCell(l1RCell);

                    Integer l1Rating = (ans != null && ans.getRevisedRating() != null)
                            ? ans.getRevisedRating()
                            : (l1r != null ? l1r.getRevisedRating() : null);
                    PdfPCell l1RatingCell = new PdfPCell(new Phrase(l1Rating != null ? l1Rating + "/5" : "—", remarkL1Font));
                    l1RatingCell.setPadding(4);
                    l1RatingCell.setBackgroundColor(new java.awt.Color(235, 252, 248));
                    l1RatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    l1RatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    qTable.addCell(l1RatingCell);

                    String l2RemarkText = (ans != null && ans.getFinalRemarks() != null)
                            ? ans.getFinalRemarks()
                            : (l2r != null && l2r.getRemarkText() != null ? l2r.getRemarkText() : "—");
                    PdfPCell l2RCell = new PdfPCell(new Phrase(l2RemarkText, remarkL2Font));
                    l2RCell.setPadding(4);
                    l2RCell.setBackgroundColor(new java.awt.Color(248, 238, 255));
                    qTable.addCell(l2RCell);

                    Integer l2Rating = (ans != null && ans.getFinalRating() != null)
                            ? ans.getFinalRating()
                            : (l2r != null ? l2r.getRevisedRating() : null);
                    PdfPCell l2RatingCell = new PdfPCell(new Phrase(l2Rating != null ? l2Rating + "/5" : "—", remarkL2Font));
                    l2RatingCell.setPadding(4);
                    l2RatingCell.setBackgroundColor(new java.awt.Color(248, 238, 255));
                    l2RatingCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                    l2RatingCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
                    qTable.addCell(l2RatingCell);

                    doc.add(qTable);
                }
                qNum++;
            }

            doc.close();

        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    // ── Employee: export their own published appraisal as Excel ──────────────
    public void exportEmployeeExcel(String employeeId, Long cycleId, HttpServletResponse response)
            throws IOException {

        SelfAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndCycle_Id(employeeId, cycleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appraisal not found"));

        if (appraisal.getStatus() != AppraisalStatus.PUBLISHED
                && appraisal.getStatus() != AppraisalStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appraisal is not yet published");
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"my_appraisal_"
                        + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("My Appraisal");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font whiteFont = wb.createFont();
            whiteFont.setBold(true);
            whiteFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(whiteFont);
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle l1Style = wb.createCellStyle();
            l1Style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            l1Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle l2Style = wb.createCellStyle();
            l2Style.setFillForegroundColor(IndexedColors.LAVENDER.getIndex());
            l2Style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] columns = {
                    "Section", "Question", "Your Answer", "Self Rating",
                    "Manager Remark (L1)", "L1 Rating",
                    "Final Approver Remark (L2)", "L2 Rating"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i <= 1 ? 10000 : 8000);
            }

            // Build remark lookups
            List<AppraisalRemark> allRemarks = remarkRepo.findByAppraisal_Id(appraisal.getId());
            Map<Long, AppraisalRemark> l1RemarkMap = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() != null)
                    .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));
            Map<Long, AppraisalRemark> l2RemarkMap = allRemarks.stream()
                    .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() != null)
                    .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));

            List<AppraisalQuestion> questions = sortedBySection(questionRepo
                    .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(appraisal.getCycle().getId()));
            List<SelfAppraisalAnswer> answers = answerRepo.findByAppraisal_Id(appraisal.getId());
            Map<Long, SelfAppraisalAnswer> ansMap = answers.stream()
                    .filter(a -> a.getQuestion() != null)
                    .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));

            int rowNum = 1;
            for (AppraisalQuestion q : questions) {
                // FIX: q.getSection() is a String — no getSectionName()
                String secName = q.getSection() != null ? q.getSection() : "General";
                boolean isSuggestion = SUGGESTION_SECTION.equalsIgnoreCase(secName);

                SelfAppraisalAnswer ans = ansMap.get(q.getId());
                AppraisalRemark l1r = l1RemarkMap.get(q.getId());
                AppraisalRemark l2r = l2RemarkMap.get(q.getId());

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(secName);
                row.createCell(1).setCellValue(q.getQuestionText());
                row.createCell(2).setCellValue(ans != null && ans.getAnswerText() != null ? ans.getAnswerText() : "");

                if (!isSuggestion && ans != null && ans.getSelfRating() != null)
                    row.createCell(3).setCellValue(ans.getSelfRating());

                // L1 remark — answer field first, fallback to remark table
                String l1RemarkText = (ans != null && ans.getRevisedRemarks() != null)
                        ? ans.getRevisedRemarks()
                        : (l1r != null ? l1r.getRemarkText() : null);
                Integer l1Rating = (ans != null && ans.getRevisedRating() != null)
                        ? ans.getRevisedRating()
                        : (l1r != null ? l1r.getRevisedRating() : null);
                if (l1RemarkText != null) {
                    Cell l1Cell = row.createCell(4);
                    l1Cell.setCellValue(l1RemarkText);
                    l1Cell.setCellStyle(l1Style);
                }
                if (!isSuggestion && l1Rating != null) {
                    Cell l1rCell = row.createCell(5);
                    l1rCell.setCellValue(l1Rating);
                    l1rCell.setCellStyle(l1Style);
                }

                // L2 remark
                String l2RemarkText = (ans != null && ans.getFinalRemarks() != null)
                        ? ans.getFinalRemarks()
                        : (l2r != null ? l2r.getRemarkText() : null);
                Integer l2Rating = (ans != null && ans.getFinalRating() != null)
                        ? ans.getFinalRating()
                        : (l2r != null ? l2r.getRevisedRating() : null);
                if (l2RemarkText != null) {
                    Cell l2Cell = row.createCell(6);
                    l2Cell.setCellValue(l2RemarkText);
                    l2Cell.setCellStyle(l2Style);
                }
                if (!isSuggestion && l2Rating != null) {
                    Cell l2rCell = row.createCell(7);
                    l2rCell.setCellValue(l2Rating);
                    l2rCell.setCellStyle(l2Style);
                }
            }

            wb.write(response.getOutputStream());
        }
    }

    // ── Helper: info cell ─────────────────────────────────────────────────────
    private void addInfoCell(PdfPTable table, String label, String value,
                             Font labelFont,
                             Font valueFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setPadding(4);
        lCell.setBackgroundColor(new java.awt.Color(245, 246, 250));
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "—", valueFont));
        vCell.setPadding(4);
        table.addCell(vCell);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String nvl(String s) { return s != null ? s : ""; }

    private void recordHistory(SelfAppraisal a, String from, String to,
                               String by, String byName,
                               AppraisalStatusHistory.ActionType actionType,
                               String remarks) {
        AppraisalStatusHistory h = new AppraisalStatusHistory();
        h.setAppraisal(a);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(by);
        h.setChangedByName(byName);
        h.setActionType(actionType);
        h.setRemarks(remarks);
        historyRepo.save(h);
    }

    private String resolveEmployeeName(String empId) {
        if (empId == null) return null;
        return employeeRepo.findByEmpId(empId).map(Employee::getName).orElse(empId);
    }

    private EmployeeAppraisalSummaryDTO buildSummary(SelfAppraisal a) {
        Employee emp = employeeRepo.findByEmpId(a.getEmployeeId()).orElse(null);
        EmployeeAppraisalSummaryDTO dto = new EmployeeAppraisalSummaryDTO();
        dto.setAppraisalId(a.getId());
        dto.setEmployeeId(a.getEmployeeId());
        dto.setEmployeeName(emp != null ? emp.getName() : a.getEmployeeId());
        dto.setCycleLabel(a.getCycle().getCycleLabel());
        dto.setStatus(a.getStatus());
        dto.setSubmittedAt(a.getSubmittedAt());
        dto.setPublishedAt(a.getPublishedAt());
        // FIX: Pass approver IDs so frontend can compute per-record tab placement
        dto.setFirstApproverId(a.getFirstApproverId());
        dto.setFinalApproverId(a.getFinalApproverId());
        return dto;
    }

    private AppraisalDetailDTO buildDetailDTO(SelfAppraisal appraisal, boolean showRemarks) {
        Employee emp = employeeRepo.findByEmpId(appraisal.getEmployeeId()).orElse(null);

        String companyExp = "N/A";
        String expType    = "Experienced";
        LocalDate dojDate = null;
        if (emp != null && emp.getOnboarding() != null
                && emp.getOnboarding().getJoiningDate() != null) {
            dojDate    = emp.getOnboarding().getJoiningDate();
            Period p   = Period.between(dojDate, LocalDate.now());
            companyExp = p.getYears() + " yr " + p.getMonths() + " mo";
            expType    = (p.getYears() == 0 && p.getMonths() < 6) ? "Fresher" : "Experienced";
        }

        List<AppraisalQuestion>   questions = questionRepo
                .findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(appraisal.getCycle().getId());
        List<SelfAppraisalAnswer> answers   = answerRepo.findByAppraisal_Id(appraisal.getId());
        List<AppraisalRemark>     remarks   = showRemarks
                ? remarkRepo.findByAppraisal_Id(appraisal.getId())
                : Collections.emptyList();

        Map<Long, SelfAppraisalAnswer> ansMap = answers.stream()
                .filter(a -> a.getQuestion() != null)
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a, b) -> b));
        Map<Long, AppraisalRemark> l1Map = remarks.stream()
                .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() != null)
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));
        Map<Long, AppraisalRemark> l2Map = remarks.stream()
                .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() != null)
                .collect(Collectors.toMap(r -> r.getQuestion().getId(), r -> r, (a, b) -> b));

        String l1OverallRemark = remarks.stream()
                .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L1 && r.getQuestion() == null)
                .map(AppraisalRemark::getRemarkText).findFirst().orElse(null);
        String l2OverallRemark = remarks.stream()
                .filter(r -> r.getApproverLevel() == AppraisalRemark.ApproverLevel.L2 && r.getQuestion() == null)
                .map(AppraisalRemark::getRemarkText).findFirst().orElse(null);

        Map<String, List<AppraisalDetailDTO.QuestionAnswerDTO>> bySection = new LinkedHashMap<>();

        // PATCH: buildDetailDTO loop — includes project lookup for "project" question
        for (AppraisalQuestion q : questions) {
            SelfAppraisalAnswer ans = ansMap.get(q.getId());
            AppraisalRemark l1r    = l1Map.get(q.getId());
            AppraisalRemark l2r    = l2Map.get(q.getId());
            boolean isSuggestion   = SUGGESTION_SECTION.equalsIgnoreCase(q.getSection());

            AppraisalDetailDTO.QuestionAnswerDTO qa = new AppraisalDetailDTO.QuestionAnswerDTO();
            qa.setQuestionId(q.getId());
            qa.setQuestionText(q.getQuestionText());
            qa.setInputType(q.getInputType().name());
            qa.setRequired(q.isRequired());
            qa.setAnswerText(ans != null ? ans.getAnswerText() : null);
            qa.setSelfRating(!isSuggestion && ans != null ? ans.getSelfRating() : null);
            qa.setRevisedRating(!isSuggestion && ans != null ? ans.getRevisedRating() : null);
            qa.setRevisedRemarks(ans != null ? ans.getRevisedRemarks() : null);
            qa.setFinalRating(!isSuggestion && ans != null ? ans.getFinalRating() : null);
            qa.setFinalRemarks(ans != null ? ans.getFinalRemarks() : null);
            qa.setL1Remark(l1r != null ? l1r.getRemarkText() : null);
            qa.setL1RevisedRating(!isSuggestion && l1r != null ? l1r.getRevisedRating() : null);
            qa.setL2Remark(l2r != null ? l2r.getRemarkText() : null);
            qa.setL2RevisedRating(!isSuggestion && l2r != null ? l2r.getRevisedRating() : null);

            // NEW: populate projects for the "project" question
            if (q.getQuestionText() != null && q.getQuestionText().toLowerCase().contains("project")) {
                List<AppraisalProject> projs =
                        projectRepo.findByAppraisal_IdAndQuestion_Id(appraisal.getId(), q.getId());
                if (!projs.isEmpty()) {
                    List<AppraisalDetailDTO.ProjectItem> items = projs.stream()
                            .map(p -> new AppraisalDetailDTO.ProjectItem(
                                    p.getId(), p.getProjectName(), p.getDescription()))
                            .collect(Collectors.toList());
                    qa.setProjects(items);
                }
            }

            bySection.computeIfAbsent(q.getSection(), k -> new ArrayList<>()).add(qa);
        }

        Map<String, List<AppraisalDetailDTO.QuestionAnswerDTO>> sorted = new LinkedHashMap<>();
        SECTION_ORDER.forEach(name -> { if (bySection.containsKey(name)) sorted.put(name, bySection.get(name)); });
        bySection.forEach((name, qs) -> sorted.putIfAbsent(name, qs));

        List<Integer> allRatedSelfRatings = new ArrayList<>();

        List<AppraisalDetailDTO.SectionDTO> sections = new ArrayList<>();
        for (Map.Entry<String, List<AppraisalDetailDTO.QuestionAnswerDTO>> e : sorted.entrySet()) {
            boolean isSuggestionSection = SUGGESTION_SECTION.equalsIgnoreCase(e.getKey());

            AppraisalDetailDTO.SectionDTO s = new AppraisalDetailDTO.SectionDTO();
            s.setSectionName(e.getKey());
            s.setQuestions(e.getValue());

            if (!isSuggestionSection) {
                List<Integer> secRatings = e.getValue().stream()
                        .map(AppraisalDetailDTO.QuestionAnswerDTO::getSelfRating)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!secRatings.isEmpty()) {
                    double avg = secRatings.stream().mapToInt(i -> i).average().orElse(0.0);
                    s.setSectionAvgRating(Math.round(avg * 100.0) / 100.0);
                    allRatedSelfRatings.addAll(secRatings);
                }
            }
            sections.add(s);
        }

        List<AppraisalDetailDTO.StatusHistoryDTO> history = historyRepo
                .findByAppraisal_IdOrderByChangedAtAsc(appraisal.getId()).stream().map(h -> {
                    AppraisalDetailDTO.StatusHistoryDTO d = new AppraisalDetailDTO.StatusHistoryDTO();
                    d.setFromStatus(h.getFromStatus());
                    d.setToStatus(h.getToStatus());
                    d.setChangedBy(h.getChangedBy());
                    d.setChangedByName(h.getChangedByName());
                    d.setActionType(h.getActionType() != null ? h.getActionType().name() : null);
                    d.setRemarks(h.getRemarks());
                    d.setChangedAt(h.getChangedAt());
                    return d;
                }).collect(Collectors.toList());

        AppraisalDetailDTO dto = new AppraisalDetailDTO();
        dto.setAppraisalId(appraisal.getId());
        dto.setEmployeeId(appraisal.getEmployeeId());
        dto.setEmployeeName(emp != null ? emp.getName() : appraisal.getEmployeeId());
        dto.setRole(emp != null && emp.getRole() != null ? emp.getRole().getRoleName() : null);
        dto.setDepartment(emp != null && emp.getDepartment() != null
                ? emp.getDepartment().getDepartmentName() : null);
        dto.setDoj(dojDate != null ? dojDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : null);
        dto.setCompanyExperience(companyExp);
        dto.setExperienceType(expType);
        dto.setReportingManager(emp != null ? emp.getReportingId() : null);
        dto.setCycleLabel(appraisal.getCycle().getCycleLabel());
        dto.setStatus(appraisal.getStatus());
        dto.setSubmittedAt(appraisal.getSubmittedAt());
        dto.setL1ReviewedAt(appraisal.getL1ReviewedAt());
        dto.setPublishedAt(appraisal.getPublishedAt());
        dto.setSections(sections);
        dto.setStatusHistory(history);
        dto.setFirstApproverId(appraisal.getFirstApproverId());
        dto.setFirstApproverName(resolveEmployeeName(appraisal.getFirstApproverId()));
        dto.setFinalApproverId(appraisal.getFinalApproverId());
        dto.setFinalApproverName(resolveEmployeeName(appraisal.getFinalApproverId()));
        dto.setL1OverallRemark(l1OverallRemark);
        dto.setL2OverallRemark(l2OverallRemark);

        if (!allRatedSelfRatings.isEmpty()) {
            double overall = allRatedSelfRatings.stream().mapToInt(i -> i).average().orElse(0.0);
            dto.setOverallAvgRating(Math.round(overall * 100.0) / 100.0);
        }

        return dto;
    }

    // ── Helper: sort questions by SECTION_ORDER then by sortOrder within section ──
    private List<AppraisalQuestion> sortedBySection(List<AppraisalQuestion> questions) {
        return questions.stream()
                .sorted(Comparator
                        .comparingInt((AppraisalQuestion q) -> {
                            int idx = SECTION_ORDER.indexOf(q.getSection());
                            return idx < 0 ? Integer.MAX_VALUE : idx;
                        })
                        .thenComparingInt(q -> q.getSortOrder()))
                .collect(Collectors.toList());
    }

    // ── Status filter helper for export ──────────────────────────────────────
    private List<SelfAppraisal> filterByStatus(List<SelfAppraisal> appraisals, String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return appraisals;
        }
        if ("PUBLISHED".equalsIgnoreCase(statusFilter)) {
            return appraisals.stream()
                    .filter(a -> a.getStatus() == AppraisalStatus.PUBLISHED
                            || a.getStatus() == AppraisalStatus.CLOSED)
                    .collect(Collectors.toList());
        }
        if ("PENDING".equalsIgnoreCase(statusFilter)) {
            return appraisals.stream()
                    .filter(a -> a.getStatus() != AppraisalStatus.PUBLISHED
                            && a.getStatus() != AppraisalStatus.CLOSED)
                    .collect(Collectors.toList());
        }
        // Exact status match (e.g. "SUBMITTED", "L1_APPROVED" etc.)
        try {
            AppraisalStatus exact = AppraisalStatus.valueOf(statusFilter.toUpperCase());
            return appraisals.stream()
                    .filter(a -> a.getStatus() == exact)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            return appraisals; // unknown filter → return all
        }
    }
}