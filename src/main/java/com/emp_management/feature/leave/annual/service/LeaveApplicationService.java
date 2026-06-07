package com.emp_management.feature.leave.annual.service;

import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.entity.EmployeePersonalDetails;
import com.emp_management.feature.employee.repository.EmployeePersonalDetailsRepository;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.holiday.utils.HolidayChecker;
import com.emp_management.feature.leave.annual.dto.LeaveApplicationResponseDTO;
import com.emp_management.feature.leave.annual.dto.LeaveApplicationWithAttachmentsDto;
import com.emp_management.feature.leave.annual.dto.LeaveRemarkDto;
import com.emp_management.feature.leave.annual.dto.LeaveResponse;
import com.emp_management.feature.leave.annual.entity.LeaveApplication;
import com.emp_management.feature.leave.annual.entity.LeaveApproval;
import com.emp_management.feature.leave.annual.entity.LeaveAttachment;
import com.emp_management.feature.leave.annual.mapper.LeaveApplicationMapper;
import com.emp_management.feature.leave.annual.mapper.LeaveApplicationWithAttachmentsDtoMapper;
import com.emp_management.feature.leave.annual.repository.LeaveApplicationRepository;
import com.emp_management.feature.leave.annual.repository.LeaveApprovalRepository;
import com.emp_management.feature.leave.annual.repository.LeaveAttachmentRepository;
import com.emp_management.feature.leave.annual.repository.LeaveTypeRepository;
import com.emp_management.feature.leave.annual.utils.DateUtils;
import com.emp_management.feature.leave.carryforward.service.CarryForwardBalanceService;
import com.emp_management.feature.leave.compoff.entity.CompOff;
import com.emp_management.feature.leave.compoff.repository.CompOffRepository;
import com.emp_management.feature.leave.compoff.service.CompOffService;
import com.emp_management.feature.notification.service.NotificationService;
import com.emp_management.shared.entity.Role;
import com.emp_management.shared.enums.*;
import com.emp_management.shared.exceptions.BadRequestException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveApplicationService {

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayChecker holidayChecker;
    private final CompOffService compOffService;
    private final CompOffRepository compOffRepository;
    private final LeaveAttachmentRepository leaveAttachmentRepository;
    private final AnnualLeaveBalanceService annualLeaveBalanceService;
    private final SickLeaveBalanceService sickLeaveBalanceService;
    private final CarryForwardBalanceService carryForwardBalanceService;// ✅ NEW FIELD
    private final EmployeePersonalDetailsRepository personalDetailsRepository;
    private final com.emp_management.feature.permission.repository.PermissionRepository permissionRepository;
    private final com.emp_management.feature.wfh.repository.WfhApplicationRepository wfhApplicationRepository;
//    private final SeparationService            separationService;

    public LeaveApplicationService(
            LeaveApplicationRepository leaveApplicationRepository,
            NotificationService notificationService,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository,
            HolidayChecker holidayChecker,
            CompOffService compOffService,
            CompOffRepository compOffRepository,
            LeaveAttachmentRepository leaveAttachmentRepository,
            AnnualLeaveBalanceService annualLeaveBalanceService,
            SickLeaveBalanceService sickLeaveBalanceService,
            CarryForwardBalanceService carryForwardBalanceService,
            EmployeePersonalDetailsRepository personalDetailsRepository,
            LeaveApprovalRepository leaveApprovalRepository,
            com.emp_management.feature.permission.repository.PermissionRepository permissionRepository,
            com.emp_management.feature.wfh.repository.WfhApplicationRepository wfhApplicationRepository
    ) {
//            SeparationService separationService) {
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.notificationService = notificationService;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.holidayChecker = holidayChecker;
        this.compOffService = compOffService;
        this.compOffRepository = compOffRepository;
        this.leaveAttachmentRepository = leaveAttachmentRepository;
        this.annualLeaveBalanceService = annualLeaveBalanceService;
        this.sickLeaveBalanceService = sickLeaveBalanceService;
        this.carryForwardBalanceService = carryForwardBalanceService; // ✅ NEW ASSIGNMENT
        this.personalDetailsRepository = personalDetailsRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.permissionRepository = permissionRepository;
        this.wfhApplicationRepository = wfhApplicationRepository;
//        this.separationService           = separationService;
    }


    // ═══════════════════════════════════════════════════════════════
    // APPLY LEAVE
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public LeaveResponse applyLeave(LeaveApplication leave) {
        leave.setYear(leave.getStartDate().getYear());

        if (leave.getEndDate().isBefore(leave.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        // ── Date restriction for SICK and ANNUAL leave (±31 days) ─────────
        String leaveTypeName = leave.getLeaveType().getLeaveType().toUpperCase();
        if ("SICK".equals(leaveTypeName) || "ANNUAL".equals(leaveTypeName)) {
            java.time.LocalDate today   = java.time.LocalDate.now();
            java.time.LocalDate minDate = today.minusDays(31);
            java.time.LocalDate maxDate = today.plusDays(31);

            if (leave.getStartDate().isBefore(minDate)) {
                throw new BadRequestException(
                        leaveTypeName + " leave can only be backdated up to 31 days.");
            }
            if (leave.getStartDate().isAfter(maxDate)) {
                throw new BadRequestException(
                        leaveTypeName + " leave can only be applied up to 31 days in advance.");
            }
        }
        // ─────────────────────────────────────────────────────────────

        checkLeaveOverlap(leave);
        checkHolidaysInRange(leave);

        BigDecimal calculatedDays = calculateLeaveDuration(leave);
        leave.setDays(calculatedDays);
        Employee employee = leave.getEmployee();
        validateLeaveTypeAndBalance(leave, employee, calculatedDays);
        setupApprovalChain(leave, employee);
        String approverName = "Auto-Approved";
        if (leave.getFirstApproverId() != null) {
            approverName = employeeRepository.findByEmpId(leave.getFirstApproverId())
                    .map(Employee::getName)
                    .orElse("Unknown Approver");
        }


        boolean isSingleDay = leave.getStartDate().isEqual(leave.getEndDate());
        String dateRange = DateUtils.formatLeaveDateRange(leave.getStartDate(), leave.getEndDate());

        String preposition = isSingleDay ? "on" : "from";

        String message = String.format("%s has applied for %s leave %s %s. Awaiting approval from: %s.",
                employee.getName(),
                leave.getLeaveType().getLeaveType(),
                preposition, // "on" or "from"
                dateRange,
                approverName);
        // No manager → auto-approve
        if (leave.getRequiredApprovalLevels() == 0) {
            leave.setStatus(RequestStatus.APPROVED);
            leave.setApprovedBy(employee.getEmpId());
            leave.setApprovedRole(employee.getRole().getRoleName());
            leave.setApprovedAt(LocalDateTime.now());
            LeaveApplication saved = leaveApplicationRepository.save(leave);
            applyBalanceDeduction(saved);
            sendNotificationToAdmin(message + " (Auto-Approved)", employee.getEmail());
            return new LeaveResponse(LeaveApplicationMapper.toDTO(saved), null);
        }

        leave.setStatus(RequestStatus.PENDING);
        LeaveApplication saved = leaveApplicationRepository.save(leave);
        notifyFirstApprover(saved, employee);
        sendNotificationToAdmin(message, employee.getEmail());
        return new LeaveResponse(LeaveApplicationMapper.toDTO(saved), null);
    }

    // ═══════════════════════════════════════════════════════════════
    // APPROVAL CHAIN SETUP
    // Uses Employee.reportingId (String empId of manager)
    // ═══════════════════════════════════════════════════════════════

    private void setupApprovalChain(LeaveApplication leave, Employee employee) {
        // reportingId is stored as Long in your entity — fetch manager by their numeric id
        String firstApproverNumericId = employee.getReportingId();

        if (firstApproverNumericId == null) {
            leave.setFirstApproverId(null);
            leave.setSecondApproverId(null);
            leave.setCurrentApproverId(null);
            leave.setCurrentApprovalLevel(null);
            leave.setRequiredApprovalLevels(0);
            return;
        }

        Employee firstApprover = employeeRepository.findByEmpId(firstApproverNumericId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "First approver not found: " + firstApproverNumericId));

        leave.setFirstApproverId(firstApprover.getEmpId());
        leave.setCurrentApproverId(firstApprover.getEmpId());
        leave.setCurrentApprovalLevel(ApprovalLevel.FIRST_APPROVER);

        String secondApproverNumericId = firstApprover.getReportingId();

        if (secondApproverNumericId == null) {
            leave.setSecondApproverId(null);
            leave.setRequiredApprovalLevels(1);
        } else {
            Employee secondApprover = employeeRepository.findByEmpId(secondApproverNumericId)
                    .orElseThrow(() -> new BadRequestException(
                            "Second approver not found: " + secondApproverNumericId));
            leave.setSecondApproverId(secondApprover.getEmpId());
            leave.setRequiredApprovalLevels(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATE LEAVE TYPE AND BALANCE
    // Driven by leaveType.leaveType (String name) — no enum switch
    // ═══════════════════════════════════════════════════════════════

    private void validateLeaveTypeAndBalance(LeaveApplication leave,
                                             Employee employee,
                                             BigDecimal days) {
        String typeName = leave.getLeaveType().getLeaveType().toUpperCase();
        int leaveYear  = leave.getStartDate().getYear();
        // Always validate against TODAY's accrued balance — not the future leave month.
        // Future leave months have no balance record yet (or a freshly created full-accrual one),
        // so using the leave month would bypass the check entirely.
        java.time.LocalDate today = java.time.LocalDate.now();
        int balanceYear  = today.getYear();
        int balanceMonth = today.getMonthValue();

        switch (typeName) {
            case "SICK"   -> validateSickLeave(leave, days, balanceYear, balanceMonth);
            case "ANNUAL" -> validateAnnualLeave(leave, days, balanceYear, balanceMonth);
            case "CARRY_FORWARD" -> validateCarryForward(leave, days, leaveYear); // ✅ NEW CASE
            case "MATERNITY" -> validateMaternity(leave, employee, days);
            case "PATERNITY" -> validatePaternity(leave, employee, days);
            case "COMP_OFF" -> validateCompOff(leave, days);
            default -> {
                // Generic validation for any future leave types:
                // Just check the total allocated days from the entity
                Double allocatedDays = leave.getLeaveType().getAllocatedDays();
                Double used = leaveApplicationRepository.getTotalUsedDaysByType(
                        employee.getEmpId(), RequestStatus.APPROVED, leaveYear,
                        leave.getLeaveType().getLeaveType());
                double available = allocatedDays - (used != null ? used : 0.0);
                if (days.doubleValue() > available) {
                    throw new BadRequestException(
                            "Insufficient " + typeName + " balance. Available: "
                                    + available + ", Requested: " + days);
                }
            }
        }
    }

    private void validateSickLeave(LeaveApplication leave, BigDecimal days, int year, int month) {
        double available = sickLeaveBalanceService
                .getAvailableForMonth(leave.getEmployeeId(), year, month);
        if (days.doubleValue() > available) {
            throw new BadRequestException(
                    "Insufficient SICK leave balance for "
                            + java.time.Month.of(month).name() + " " + year
                            + ". Available: " + String.format("%.1f", available)
                            + ", Requested: " + days);
        }
    }

    private void validateAnnualLeave(LeaveApplication leave, BigDecimal days, int year, int month) {
        double available = annualLeaveBalanceService
                .getAvailableForMonth(leave.getEmployeeId(), year, month);
        if (days.doubleValue() > available) {
            throw new BadRequestException(
                    "Insufficient ANNUAL_LEAVE balance for "
                            + java.time.Month.of(month).name() + " " + year
                            + ". Available: " + String.format("%.1f", available)
                            + ", Requested: " + days);
        }
    }

    /**
     * ✅ NEW — Validates carry-forward balance before allowing application.
     * <p>
     * Reads CarryForwardBalance.remaining for the employee in the leave year.
     * If no balance record exists → employee has 0 carry-forward → reject.
     * Balance is a yearly bucket (not monthly), so we only check by year.
     */
    private void validateCarryForward(LeaveApplication leave, BigDecimal days, int year) {
        double available = carryForwardBalanceService
                .getAvailableBalance(leave.getEmployee().getEmpId(), year);
        if (days.doubleValue() > available) {
            throw new BadRequestException(
                    "Insufficient CARRY_FORWARD balance for year " + year
                            + ". Available: " + String.format("%.1f", available)
                            + ", Requested: " + days);
        }
    }

    private void validateMaternity(LeaveApplication leave, Employee employee, BigDecimal days) {
        EmployeePersonalDetails details = personalDetailsRepository
                .findByEmployee_EmpId(employee.getEmpId()).orElse(null);
        if (details == null || details.getGender() != Gender.FEMALE) {
            throw new BadRequestException("MATERNITY leave is only available for female employees.");
        }
        double maxDays = leave.getLeaveType().getAllocatedDays();
        if (days.doubleValue() > maxDays) {
            throw new BadRequestException(
                    "MATERNITY leave cannot exceed " + maxDays + " days.");
        }
        Double used = leaveApplicationRepository.getTotalUsedDaysByType(
                employee.getEmpId(), RequestStatus.APPROVED,
                leave.getStartDate().getYear(), leave.getLeaveType().getLeaveType());
        if (used != null && used > 0) {
            throw new BadRequestException("MATERNITY leave has already been taken this year.");
        }
    }

    private void validatePaternity(LeaveApplication leave, Employee employee, BigDecimal days) {
        EmployeePersonalDetails details = personalDetailsRepository
                .findByEmployee_EmpId(employee.getEmpId()).orElse(null);
        if (details == null || details.getGender() != Gender.MALE) {
            throw new BadRequestException("PATERNITY leave is only available for male employees.");
        }
        double maxDays = leave.getLeaveType().getAllocatedDays();
        if (days.doubleValue() > maxDays) {
            throw new BadRequestException(
                    "PATERNITY leave cannot exceed " + maxDays + " days.");
        }
        Double used = leaveApplicationRepository.getTotalUsedDaysByType(
                employee.getEmpId(), RequestStatus.APPROVED,
                leave.getStartDate().getYear(), leave.getLeaveType().getLeaveType());
        if (used != null && used > 0) {
            throw new BadRequestException("PATERNITY leave has already been taken this year.");
        }
    }

    private void validateCompOff(LeaveApplication leave, BigDecimal days) {
        BigDecimal available = compOffService.getAvailableCompOffDays(leave.getEmployeeId());
        if (available.compareTo(days) < 0) {
            throw new BadRequestException(
                    "Insufficient Comp-Off balance. Available: " + available
                            + ", Requested: " + days);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BALANCE DEDUCTION / RESTORE
    // ═══════════════════════════════════════════════════════════════

    public void applyBalanceDeduction(LeaveApplication leave) {
        double days = leave.getDays().doubleValue();
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String empId = leave.getEmployeeId();
        String type = leave.getLeaveType().getLeaveType().toUpperCase();

        switch (type) {
            case "ANNUAL" -> annualLeaveBalanceService.deductLeave(empId, year, month, days);
            case "SICK" -> sickLeaveBalanceService.deductLeave(empId, year, month, days);
            case "CARRY_FORWARD" -> carryForwardBalanceService.deductLeave(empId, year, days); // ✅ NEW
            case "COMP_OFF" -> compOffService.useCompOff(empId, leave.getDays(), leave.getId());
            default -> { /* MATERNITY/PATERNITY — no balance table */ }
        }

//        if (separationService.isInNoticePeriod(empId)) {

//            separationService.extendNoticePeriod(empId, (int) Math.ceil(days));
//        }
    }

    public void restoreBalance(LeaveApplication leave) {
        double days = leave.getDays().doubleValue();
        int year = leave.getStartDate().getYear();
        int month = leave.getStartDate().getMonthValue();
        String empId = leave.getEmployeeId();
        String type = leave.getLeaveType().getLeaveType().toUpperCase();

        switch (type) {
            case "ANNUAL" -> annualLeaveBalanceService.restoreLeave(empId, year, month, days);
            case "SICK" -> sickLeaveBalanceService.restoreLeave(empId, year, month, days);
            case "CARRY_FORWARD" -> carryForwardBalanceService.restoreLeave(empId, year, days); // ✅ NEW
            case "COMP_OFF" -> {
                List<CompOff> linked = compOffRepository.findByUsedLeaveApplicationId(leave.getId());
                BigDecimal restored = BigDecimal.ZERO;
                for (CompOff c : linked) {
                    c.setStatus(RequestStatus.EARNED);
                    c.setUsedLeaveApplicationId(null);
                    compOffRepository.save(c);
                    restored = restored.add(c.getDays());
                }
                if (restored.compareTo(BigDecimal.ZERO) > 0) {
                    compOffService.restoreCompOffBalance(empId, restored);
                }
            }
            default -> { /* MATERNITY/PATERNITY — no balance table */ }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CANCEL LEAVE
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void cancelEmployeeLeave(Long applicationId, String employeeId) {
        LeaveApplication leave = leaveApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BadRequestException(
                        "Leave application not found with ID: " + applicationId));

        if (!leave.getEmployeeId().equals(employeeId)) {
            throw new BadRequestException("Unauthorized: You cannot cancel another employee's leave.");
        }
        if (leave.getStatus() == RequestStatus.REJECTED
                || leave.getStatus() == RequestStatus.CANCELLED) {
            throw new BadRequestException("Leave is already finalized as " + leave.getStatus());
        }
        if (leave.getStatus() == RequestStatus.APPROVED) {
            restoreBalance(leave);
        }
        leave.setStatus(RequestStatus.CANCELLED);
        leaveApplicationRepository.save(leave);
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE LEAVE (before approval)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public LeaveResponse updateLeave(Long id, String employeeId,
                                     LocalDate startDate, LocalDate endDate,
                                     String reason,
                                     String startDateHalfDayType,
                                     String endDateHalfDayType) {
        LeaveApplication leave = leaveApplicationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(
                        "Leave application not found with ID: " + id));

        if (!leave.getEmployeeId().equals(employeeId)) {
            throw new BadRequestException("Unauthorized: You can only update your own leaves");
        }
        if (leave.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException(
                    "Can only update PENDING leaves. Current status: " + leave.getStatus());
        }

        if (startDate != null) leave.setStartDate(startDate);
        if (endDate != null) leave.setEndDate(endDate);
        if (reason != null && !reason.isEmpty()) leave.setReason(reason);

        if (startDateHalfDayType != null) {
            leave.setStartDateHalfDayType(
                    startDateHalfDayType.isBlank() ? null
                            : HalfDayType.valueOf(startDateHalfDayType.toUpperCase()));
        }
        if (endDateHalfDayType != null) {
            leave.setEndDateHalfDayType(
                    endDateHalfDayType.isBlank() ? null
                            : HalfDayType.valueOf(endDateHalfDayType.toUpperCase()));
        }
        if (leave.getEndDate().isBefore(leave.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        leave.setDays(calculateLeaveDuration(leave));
        return new LeaveResponse(LeaveApplicationMapper.toDTO(leaveApplicationRepository.save(leave)), null);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════

    public LeaveApplicationWithAttachmentsDto getLeaveById(Long id) {
        LeaveApplication leave = leaveApplicationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(
                        "Leave application not found with ID: " + id));
        List<LeaveAttachment> attachments = leaveAttachmentRepository.findByLeaveApplicationId(leave.getId());

        List<LeaveApproval> remarks = leaveApprovalRepository.findByLeaveIdInOrderByDecidedAtAsc(List.of(leave.getId()));
        return LeaveApplicationWithAttachmentsDtoMapper.toDTO(leave, attachments, remarks);
    }

    public List<LeaveApplicationResponseDTO> getLeavesByEmployee(String employeeId) {
        return leaveApplicationRepository
                .findByEmployee_EmpId(employeeId)
                .stream()
                .map(LeaveApplicationMapper::toDTO)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // LEAVE DURATION (logic unchanged)
    // ═══════════════════════════════════════════════════════════════

    public BigDecimal calculateLeaveDuration(LeaveApplication leave) {
        LocalDate startDate = leave.getStartDate();
        LocalDate endDate = leave.getEndDate();

        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("End date cannot be before start date.");
        }

        boolean sameDay = startDate.isEqual(endDate);
        boolean startIsHalf = leave.getStartDateHalfDayType() != null;
        boolean endIsHalf = leave.getEndDateHalfDayType() != null;

        if (sameDay) {
            return (startIsHalf || endIsHalf) ? new BigDecimal("0.5") : BigDecimal.ONE;
        }

        long workingDays = holidayChecker.countWorkingDays(startDate, endDate);
        if (workingDays <= 0) {
            throw new BadRequestException(
                    "No working days found in the selected date range.");
        }

        BigDecimal days = BigDecimal.valueOf(workingDays);
        if (startIsHalf && !holidayChecker.isNonWorkingDay(startDate))
            days = days.subtract(new BigDecimal("0.5"));
        if (endIsHalf && !holidayChecker.isNonWorkingDay(endDate))
            days = days.subtract(new BigDecimal("0.5"));

        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "Calculated leave duration is zero or negative. Check dates and half-day selections.");
        }
        return days;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void checkHolidaysInRange(LeaveApplication leave) {
        LocalDate start = leave.getStartDate();
        LocalDate end = leave.getEndDate();
        if (start.isEqual(end)) {
            String reason = holidayChecker.getNonWorkingDayReason(start);
            if (reason != null)
                throw new BadRequestException(
                        "Cannot apply leave on " + start + " — non-working day: " + reason);
            return;
        }
        if (holidayChecker.countWorkingDays(start, end) == 0) {
            throw new BadRequestException(
                    "The date range (" + start + " to " + end + ") contains no working days.");
        }
    }

    private void checkLeaveOverlap(LeaveApplication leave) {
        List<LeaveApplication> overlaps = leaveApplicationRepository.findOverlappingLeaves(
                leave.getEmployeeId(), leave.getStartDate(), leave.getEndDate());
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Leave dates overlap with an existing leave.");
        }
    }

    private void notifyFirstApprover(LeaveApplication leave, Employee employee) {
        if (leave.getFirstApproverId() == null) return;

        Employee approver = employeeRepository.findByEmpId(leave.getFirstApproverId())
                .orElseThrow(() -> new EntityNotFoundException("Approver not found"));

        String dateRange = DateUtils.formatLeaveDateRange(leave.getStartDate(), leave.getEndDate());

        boolean isSingleDay = leave.getStartDate().isEqual(leave.getEndDate());
        String timePhrase = isSingleDay ? "on" : "from";

        notificationService.createNotification(
                approver.getEmpId(),
                employee.getEmail(),
                approver.getEmail(),
                EventType.LEAVE_APPLIED,
                Channel.EMAIL,
                String.format("%s applied for %s leave %s %s. Awaiting your approval.",
                        employee.getName(),
                        leave.getLeaveType().getLeaveType(),
                        timePhrase,
                        dateRange));
    }

    private void sendNotificationToAdmin(String message, String senderEmail) {
        Employee admin = getAdmin();

        // Safety check: if no admin exists, don't try to send
        if (admin == null) {
            System.out.println("No admin found to notify.");
            return;
        }

        notificationService.createNotification(
                admin.getEmpId(),       // Use the EMP_ID, not the email
                senderEmail,            // From Email
                admin.getEmail(),       // To (Target email for the record)
                EventType.LEAVE_APPLIED,
                Channel.EMAIL,
                message
        );
    }

    private Employee getAdmin() {
        return employeeRepository.findAllByRoleName("ADMIN")
                .stream()
                .findFirst()
                .orElse(null); // Handle cases where no admin is found
    }

    // ════════════════════════════════════════════════════════════════
    //  LEAVE EXPORT — Leave + WFH + Permission merged
    // ════════════════════════════════════════════════════════════════

    private static final java.time.format.DateTimeFormatter EXPORT_DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("d-MMM-yy");

    public List<com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO> getLeaveExportAll(
            java.time.LocalDate from, java.time.LocalDate to) {
        List<com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO> rows = new java.util.ArrayList<>();
        leaveApplicationRepository.findByStartDateBetweenOrCreatedAtBetween(from, to)
                .stream().map(this::toExportRow).forEach(rows::add);
        wfhApplicationRepository.findByStartDateBetweenOrderByStartDateAsc(from, to)
                .stream().map(this::wfhToExportRow).forEach(rows::add);
        permissionRepository.findByPermissionDateBetweenOrderByPermissionDateAsc(from, to)
                .stream().map(this::permissionToExportRow).forEach(rows::add);
        rows.sort(java.util.Comparator.comparing(
                com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO::getApplicationCreatedDate,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return rows;
    }

    public List<com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO> getLeaveExportForTeam(
            List<String> empIds, java.time.LocalDate from, java.time.LocalDate to) {
        List<com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO> rows = new java.util.ArrayList<>();
        leaveApplicationRepository.findByEmployeeIdsAndDateRange(empIds, from, to)
                .stream().map(this::toExportRow).forEach(rows::add);
        wfhApplicationRepository.findByEmployee_EmpIdInAndStartDateBetweenOrderByStartDateAsc(empIds, from, to)
                .stream().map(this::wfhToExportRow).forEach(rows::add);
        permissionRepository.findByEmployee_EmpIdInAndPermissionDateBetweenOrderByPermissionDateAsc(empIds, from, to)
                .stream().map(this::permissionToExportRow).forEach(rows::add);
        rows.sort(java.util.Comparator.comparing(
                com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO::getApplicationCreatedDate,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return rows;
    }

    public java.io.ByteArrayInputStream exportLeaveToExcel(
            List<com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO> rows) {
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Leave Export");
            String[] cols = {
                    "Application\nCreated Date","Employee ID","Employee Name","Leave Type",
                    "Start Date","End Date","Start of\nthe Day","No.Of Days","Leave Year",
                    "First Approver","First Approval\nDate","First Approval\nDecision",
                    "Second Approver","Second Approval\nDate","Second Approval\nDecision"
            };
            org.apache.poi.ss.usermodel.CellStyle hStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
            hFont.setBold(true); hFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_TEAL.getIndex());
            hStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            hStyle.setWrapText(true);
            hStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            org.apache.poi.ss.usermodel.Row hRow = sheet.createRow(0);
            hRow.setHeightInPoints(36);
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = hRow.createCell(i);
                c.setCellValue(cols[i]); c.setCellStyle(hStyle);
            }
            int rowIdx = 1;
            for (com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO row : rows) {
                org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
                String[] vals = {
                        row.getApplicationCreatedDate(), row.getEmployeeId(), row.getEmployeeName(),
                        row.getLeaveType(), row.getStartDate(), row.getEndDate(),
                        row.getStartOfTheDay(), row.getNoOfDays(), row.getLeaveYear(),
                        row.getFirstApprover(), row.getFirstApprovalDate(), row.getFirstApprovalDecision(),
                        row.getSecondApprover(), row.getSecondApprovalDate(), row.getSecondApprovalDecision()
                };
                for (int i = 0; i < vals.length; i++) r.createCell(i).setCellValue(vals[i] != null ? vals[i] : "");
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return new java.io.ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) { throw new RuntimeException("Leave export failed", e); }
    }

    private com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO toExportRow(LeaveApplication a) {
        com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO r = new com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO();
        r.setApplicationCreatedDate(a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate().format(EXPORT_DATE_FMT) : "");
        r.setEmployeeId(a.getEmployeeId());
        r.setEmployeeName(a.getEmployeeName());
        r.setLeaveType(a.getLeaveType() != null ? a.getLeaveType().getLeaveType() : "");
        r.setStartDate(a.getStartDate() != null ? a.getStartDate().format(EXPORT_DATE_FMT) : "");
        r.setEndDate(a.getEndDate() != null ? a.getEndDate().format(EXPORT_DATE_FMT) : "");
        r.setStartOfTheDay(a.getStartDateHalfDayType() != null ? a.getStartDateHalfDayType().name() : "NULL");
        r.setNoOfDays(a.getDays() != null ? a.getDays().stripTrailingZeros().toPlainString() : "0");
        r.setLeaveYear(a.getYear() != null ? String.valueOf(a.getYear()) : "");
        r.setFirstApprover(resolveApproverName(a.getFirstApproverId()));
        r.setFirstApprovalDate(a.getFirstApproverDecidedAt() != null ? a.getFirstApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setFirstApprovalDecision(a.getFirstApproverDecision() != null ? a.getFirstApproverDecision().name() : "NULL");
        r.setSecondApprover(resolveApproverName(a.getSecondApproverId()));
        r.setSecondApprovalDate(a.getSecondApproverDecidedAt() != null ? a.getSecondApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setSecondApprovalDecision(a.getSecondApproverDecision() != null ? a.getSecondApproverDecision().name() : (a.getSecondApproverId() != null ? "PENDING" : "N/A"));
        return r;
    }

    private com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO wfhToExportRow(com.emp_management.feature.wfh.entity.WfhApplication w) {
        com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO r = new com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO();
        r.setApplicationCreatedDate(w.getCreatedAt() != null ? w.getCreatedAt().toLocalDate().format(EXPORT_DATE_FMT) : "");
        r.setEmployeeId(w.getEmployeeId()); r.setEmployeeName(w.getEmployeeName());
        r.setLeaveType("WFH");
        r.setStartDate(w.getStartDate() != null ? w.getStartDate().format(EXPORT_DATE_FMT) : "");
        r.setEndDate(w.getEndDate() != null ? w.getEndDate().format(EXPORT_DATE_FMT) : "");
        r.setStartOfTheDay(w.getStartDateHalfDayType() != null ? w.getStartDateHalfDayType().name() : "NULL");
        r.setNoOfDays(w.getTotalDays() != null ? w.getTotalDays().stripTrailingZeros().toPlainString() : "0");
        r.setLeaveYear(w.getStartDate() != null ? String.valueOf(w.getStartDate().getYear()) : "");
        r.setFirstApprover(resolveApproverName(w.getFirstApproverId()));
        r.setFirstApprovalDate(w.getFirstApproverDecidedAt() != null ? w.getFirstApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setFirstApprovalDecision(w.getFirstApproverDecision() != null ? w.getFirstApproverDecision().name() : "NULL");
        r.setSecondApprover(resolveApproverName(w.getSecondApproverId()));
        r.setSecondApprovalDate(w.getSecondApproverDecidedAt() != null ? w.getSecondApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setSecondApprovalDecision(w.getSecondApproverDecision() != null ? w.getSecondApproverDecision().name() : (w.getSecondApproverId() != null ? "PENDING" : "N/A"));
        return r;
    }

    private com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO permissionToExportRow(com.emp_management.feature.permission.entity.Permission p) {
        com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO r = new com.emp_management.feature.leave.annual.dto.LeaveExportRowDTO();
        r.setApplicationCreatedDate(p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate().format(EXPORT_DATE_FMT) : "");
        r.setEmployeeId(p.getEmployeeId()); r.setEmployeeName(p.getEmployeeName());
        r.setLeaveType("PERMISSION");
        String pDate = p.getPermissionDate() != null ? p.getPermissionDate().format(EXPORT_DATE_FMT) : "";
        r.setStartDate(pDate); r.setEndDate(pDate);
        String st = p.getStartTime() != null ? p.getStartTime().toString().substring(0, 5) : "";
        String et = p.getEndTime() != null ? p.getEndTime().toString().substring(0, 5) : "";
        r.setStartOfTheDay(st.isEmpty() ? "NULL" : st + " - " + et);
        r.setNoOfDays(p.getDurationMinutes() != null ? p.getDurationMinutes() + " min" : "NULL");
        r.setLeaveYear(p.getPermissionDate() != null ? String.valueOf(p.getPermissionDate().getYear()) : "");
        r.setFirstApprover(resolveApproverName(p.getFirstApproverId()));
        r.setFirstApprovalDate(p.getFirstApproverDecidedAt() != null ? p.getFirstApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setFirstApprovalDecision(p.getFirstApproverDecision() != null ? p.getFirstApproverDecision().name() : "NULL");
        r.setSecondApprover(resolveApproverName(p.getSecondApproverId()));
        r.setSecondApprovalDate(p.getSecondApproverDecidedAt() != null ? p.getSecondApproverDecidedAt().toLocalDate().format(EXPORT_DATE_FMT) : "NULL");
        r.setSecondApprovalDecision(p.getSecondApproverDecision() != null ? p.getSecondApproverDecision().name() : (p.getSecondApproverId() != null ? "PENDING" : "N/A"));
        return r;
    }

    private String resolveApproverName(String empId) {
        if (empId == null || empId.isBlank()) return "";
        return employeeRepository.findByEmpId(empId).map(Employee::getName).orElse(empId);
    }
}