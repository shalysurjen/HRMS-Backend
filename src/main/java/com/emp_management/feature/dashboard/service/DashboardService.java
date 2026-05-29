package com.emp_management.feature.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.emp_management.feature.dashboard.dto.*;
import com.emp_management.feature.employee.entity.Employee;
import com.emp_management.feature.employee.entity.EmployeeOnboarding;
import com.emp_management.feature.employee.repository.EmployeePersonalDetailsRepository;
import com.emp_management.feature.employee.repository.EmployeeRepository;
import com.emp_management.feature.leave.annual.dto.LeaveApplicationResponseDTO;
import com.emp_management.feature.leave.annual.entity.*;
import com.emp_management.feature.leave.annual.mapper.LeaveApplicationMapper;
import com.emp_management.feature.leave.annual.repository.*;
import com.emp_management.feature.leave.annual.service.AnnualLeaveBalanceService;
import com.emp_management.feature.leave.annual.service.SickLeaveBalanceService;
import com.emp_management.feature.leave.carryforward.entity.CarryForwardBalance;
import com.emp_management.feature.leave.carryforward.repository.CarryForwardBalanceRepository;
import com.emp_management.feature.leave.compoff.entity.CompOffBalance;
import com.emp_management.feature.leave.compoff.repository.CompOffBalanceRepository;
// ── NEW imports for permission calendar ───────────────────────────
import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.feature.permission.repository.PermissionRepository;
import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.feature.wfh.repository.WfhApplicationRepository;
import com.emp_management.shared.enums.RequestStatus;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final WfhApplicationRepository wfhApplicationRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveAllocationRepository allocationRepository;
    private final LeaveApplicationRepository applicationRepository;
    private final CompOffBalanceRepository compOffRepository;
    private final CarryForwardBalanceRepository carryForwardRepository;
    //    private final LopRecordRepository                   lopRepository;
//    private final ODRequestRepository                   odRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AnnualLeaveMonthlyBalanceRepository annualLeaveMonthlyBalanceRepository;
    private final SickLeaveMonthlyBalanceRepository     sickLeaveMonthlyBalanceRepository;
    private final EmployeePersonalDetailsRepository employeePersonalDetailsRepository;
    private final AnnualLeaveBalanceService annualLeaveBalanceService;
    private final SickLeaveBalanceService sickLeaveBalanceService;
    private final PermissionRepository permissionRepository;  // ← ADD

    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveAllocationRepository allocationRepository,
                            WfhApplicationRepository wfhApplicationRepository,
                            LeaveApplicationRepository applicationRepository,
                            CompOffBalanceRepository compOffRepository,
                            CarryForwardBalanceRepository carryForwardRepository,
//                            LopRecordRepository lopRepository,
//                            ODRequestRepository odRepository,
                            AnnualLeaveMonthlyBalanceRepository annualLeaveMonthlyBalanceRepository,
                            LeaveTypeRepository leaveTypeRepository,
                            SickLeaveMonthlyBalanceRepository sickLeaveMonthlyBalanceRepository,
                            EmployeePersonalDetailsRepository employeePersonalDetailsRepository,
                            AnnualLeaveBalanceService annualLeaveBalanceService,
                            SickLeaveBalanceService sickLeaveBalanceService,
                            PermissionRepository permissionRepository) {
        this.employeeRepository                 = employeeRepository;
        this.allocationRepository               = allocationRepository;
        this.applicationRepository              = applicationRepository;
        this.compOffRepository                  = compOffRepository;
        this.carryForwardRepository             = carryForwardRepository;
//        this.lopRepository                      = lopRepository;
//        this.odRepository                       = odRepository;
        this.annualLeaveMonthlyBalanceRepository = annualLeaveMonthlyBalanceRepository;
        this.sickLeaveMonthlyBalanceRepository  = sickLeaveMonthlyBalanceRepository;
        this.employeePersonalDetailsRepository  = employeePersonalDetailsRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.annualLeaveBalanceService = annualLeaveBalanceService;
        this.sickLeaveBalanceService = sickLeaveBalanceService;
        this.permissionRepository = permissionRepository;
        this.wfhApplicationRepository = wfhApplicationRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    // EMPLOYEE DASHBOARD
    // ═══════════════════════════════════════════════════════════════

    public EmployeeDashboardResponse getDashboard(String employeeId) {
        log.info("DASHBOARD employee={}", employeeId);

        Employee employee = employeeRepository.findByEmpId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

        int currentYear  = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        annualLeaveBalanceService.initializeForCurrentMonth(employeeId, currentYear, currentMonth);
        sickLeaveBalanceService.initializeForCurrentMonth(employeeId, currentYear, currentMonth);

        EmployeeDashboardResponse response = new EmployeeDashboardResponse();
        response.setEmployeeId(employeeId);
        response.setEmployeeName(employee.getName());
        response.setCurrentYear(currentYear);
        response.setLastUpdated(LocalDateTime.now());

        // ── Fetch allocations ──────────────────────────────────────────
        List<LeaveAllocation> allocations =
                allocationRepository.findByEmployee_EmpIdAndYear(employeeId, currentYear);

        // ── Restricted type names (MATERNITY, PATERNITY etc.) ─────────
        // These are excluded from yearly allocated/used/balance totals
        Set<String> restrictedTypeNames = leaveTypeRepository.findRestrictedLeaveTypes()
                .stream()
                .map(LeaveType::getLeaveType)
                .collect(Collectors.toSet());

        // ── Yearly totals — EXCLUDE restricted leave types ────────────
        double yearlyAllocated = allocations.stream()
                .filter(a -> !restrictedTypeNames.contains(
                        a.getLeaveCategory().getLeaveType()))
                .mapToDouble(LeaveAllocation::getAllocatedDays)
                .sum();

        // For used days — query all approved, then filter out restricted types in memory
        List<LeaveApplication> allApprovedThisYear = applicationRepository
                .findByEmployee_EmpIdAndStatusAndYear(employeeId, RequestStatus.APPROVED, currentYear);

        double yearlyUsed = allApprovedThisYear.stream()
                .filter(l -> !restrictedTypeNames.contains(
                        l.getLeaveType().getLeaveType()))
                .mapToDouble(l -> l.getDays().doubleValue())
                .sum();

        response.setYearlyAllocated(yearlyAllocated);
        response.setYearlyUsed(yearlyUsed);
        response.setYearlyBalance(yearlyAllocated - yearlyUsed);

        // ── Monthly ANNUAL_LEAVE balance ───────────────────────────────
        AnnualLeaveMonthlyBalance annualMonthly = annualLeaveMonthlyBalanceRepository
                .findByEmployeeIdAndYearAndMonth(employeeId, currentYear, currentMonth)
                .orElse(null);

        double annualAvailable = annualMonthly != null ? annualMonthly.getAvailableDays() : 0.0;
        double annualUsed      = annualMonthly != null ? annualMonthly.getUsedDays()      : 0.0;
        double annualRemaining = annualMonthly != null ? annualMonthly.getRemainingDays() : 0.0;

        response.setMonthlyAnnualAllocated(annualAvailable);
        response.setMonthlyAnnualUsed(annualUsed);
        response.setMonthlyAnnualBalance(annualRemaining);

        // ── Monthly SICK balance ───────────────────────────────────────
        SickLeaveMonthlyBalance sickMonthly = sickLeaveMonthlyBalanceRepository
                .findByEmployeeIdAndYearAndMonth(employeeId, currentYear, currentMonth)
                .orElse(null);

        double sickAvailable = sickMonthly != null ? sickMonthly.getAvailableDays() : 0.0;
        double sickUsed      = sickMonthly != null ? sickMonthly.getUsedDays()      : 0.0;
        double sickRemaining = sickMonthly != null ? sickMonthly.getRemainingDays() : 0.0;

        response.setMonthlySickAllocated(sickAvailable);
        response.setMonthlySickUsed(sickUsed);
        response.setMonthlySickBalance(sickRemaining);
        response.setMonthlyTotalBalance(annualRemaining + sickRemaining);

        // ── Carry forward ──────────────────────────────────────────────
        CarryForwardBalance cf = carryForwardRepository
                .findByEmployee_EmpIdAndYear(employeeId, currentYear).orElse(null);
        response.setCarryForwardTotal(cf != null ? cf.getTotalCarriedForward() : 0.0);
        response.setCarryForwardUsed(cf != null ? cf.getTotalUsed()            : 0.0);
        response.setCarryForwardRemaining(cf != null ? cf.getRemaining()       : 0.0);

        // ── Comp-off ───────────────────────────────────────────────────
        CompOffBalance compOff = compOffRepository
                .findByEmployeeIdAndYear(employeeId, currentYear).orElse(null);
        double coBal = compOff != null ? compOff.getBalance() : 0.0;
        response.setCompoffBalance(coBal);

        // ── Leave counts (all types including restricted) ──────────────
        response.setApprovedCount(safeCount(applicationRepository
                .countByStatus(employeeId, currentYear, RequestStatus.APPROVED)));
        response.setRejectedCount(safeCount(applicationRepository
                .countByStatus(employeeId, currentYear, RequestStatus.REJECTED)));
        response.setPendingCount(safeCount(applicationRepository
                .countByStatus(employeeId, currentYear, RequestStatus.PENDING)));

        // ── Breakdown by leave type ────────────────────────────────────
        List<LeaveApplication> pendingLeaves = applicationRepository
                .findByEmployee_EmpIdAndStatusAndYear(
                        employeeId, RequestStatus.PENDING, currentYear);

        // Group approved/pending by leave type name
        Map<String, List<LeaveApplication>> byType = allApprovedThisYear.stream()
                .collect(Collectors.groupingBy(l -> l.getLeaveType().getLeaveType()));
        Map<String, List<LeaveApplication>> pendingByType = pendingLeaves.stream()
                .collect(Collectors.groupingBy(l -> l.getLeaveType().getLeaveType()));

        List<LeaveTypeBreakdown> breakdown = new ArrayList<>();
        for (LeaveAllocation allocation : allocations) {
            String typeName  = allocation.getLeaveCategory().getLeaveType();
            double allocated = allocation.getAllocatedDays();

            List<LeaveApplication> typeLeaves = byType.getOrDefault(typeName, List.of());
            double used = typeLeaves.stream()
                    .mapToDouble(l -> l.getDays().doubleValue()).sum();

            // Restricted types show their own used/allocated directly
            // Non-restricted ANNUAL/SICK use the monthly cumulative balance
            double remaining;
            if (restrictedTypeNames.contains(typeName)) {
                remaining = allocated - used;   // simple calc — no monthly accrual
            } else {
                remaining = switch (typeName.toUpperCase()) {
                    case "ANNUAL" -> allocated - used;
                    case "SICK" -> allocated - used;
                    default -> allocated - used;
                };
            }

            int  halfDays    = (int) typeLeaves.stream()
                    .filter(l -> l.getDays().compareTo(new BigDecimal("0.5")) == 0)
                    .count();
            long pendingCount = pendingByType.getOrDefault(typeName, List.of()).size();

            breakdown.add(new LeaveTypeBreakdown(
                    typeName, allocated, used, remaining, halfDays, pendingCount));
        }

        // Add COMP_OFF row
        double coEarned = compOff != null ? compOff.getEarned() : 0.0;
        double coUsed   = compOff != null ? compOff.getUsed()   : 0.0;
        long   coPending = pendingByType.getOrDefault("COMP_OFF", List.of()).size();
        breakdown.add(new LeaveTypeBreakdown("COMP_OFF", coEarned, coUsed, coBal, 0, coPending));

        response.setBreakdown(breakdown);

        return response;
    }
    // ── LOP ───────────────────────────────────────────────────
//        Double totalLOP = lopRepository.sumLopDaysForYear(employeeId, currentYear);
//        response.setLossOfPayPercentage(totalLOP != null ? totalLOP : 0.0);
    // ═══════════════════════════════════════════════════════════════
    // MONTHLY STATS
    // ═══════════════════════════════════════════════════════════════

    public MonthlyStatsResponse getMonthlyStats(String employeeId, Integer year, Integer month) {
        employeeRepository.findByEmpId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        MonthlyStatsResponse response = new MonthlyStatsResponse();
        response.setEmployeeId(employeeId);
        response.setYear(year);
        response.setMonth(month);

        AnnualLeaveMonthlyBalance balance = annualLeaveMonthlyBalanceRepository
                .findByEmployeeIdAndYearAndMonth(employeeId, year, month).orElse(null);

        double used      = balance != null ? balance.getUsedDays()      : 0.0;
        double available = balance != null ? balance.getAvailableDays() : 0.0;

        response.setTotalApprovedCount((int) used);
        response.setExceededLimit(used > available);
        return response;
    }

    // ═══════════════════════════════════════════════════════════════
    // TEAM MEMBERS
    // ═══════════════════════════════════════════════════════════════

    public List<TeamMember> getTeamMembers(String managerId) {
        return employeeRepository.findActiveTeamMembers(managerId).stream()
                .map(member -> {
                    TeamMember dto = new TeamMember();
                    dto.setEmployeeId(member.getEmpId());
                    dto.setEmployeeName(member.getName());
                    employeePersonalDetailsRepository
                            .findByEmployee_EmpId(member.getEmpId())
                            .ifPresent(d -> {
                                dto.setDesignation(d.getDesignation());
                                dto.setSkills(d.getSkillSet());
                            });
                    return dto;
                }).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // TEAM BALANCES
    // ═══════════════════════════════════════════════════════════════

    public List<TeamMemberBalance> getTeamBalances(String managerId, Integer year) {
        return employeeRepository.findActiveTeamMembers(managerId).stream()
                .map(member -> {
                    String empId = member.getEmpId();
                    TeamMemberBalance b = new TeamMemberBalance();
                    b.setEmployeeId(empId);
                    b.setEmployeeName(member.getName());

                    Double alloc = allocationRepository.getTotalAllocatedDays(empId, year);
                    Double used  = applicationRepository.getTotalUsedDays(
                            empId, RequestStatus.APPROVED, year);
                    b.setTotalAllocated(alloc != null ? alloc : 0.0);
                    b.setTotalUsed(used != null ? used : 0.0);
                    b.setTotalRemaining(b.getTotalAllocated() - b.getTotalUsed());

                    compOffRepository.findByEmployeeIdAndYear(empId, year)
                            .ifPresent(co -> b.setCompOffBalance(co.getBalance()));

//                    Double lop = lopRepository.sumLopDaysForYear(empId, year);
//                    b.setLopPercentage(lop != null ? lop : 0.0);
                    return b;
                }).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // MANAGER DASHBOARD
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ManagerDashboardResponse getManagerDashboard(String managerId) {
        ManagerDashboardResponse response = new ManagerDashboardResponse();
        response.setPersonalStats(getDashboard(managerId));

        List<Employee> team = employeeRepository.findActiveTeamMembers(managerId);
        response.setTeamSize(team.size());

        // Pending team requests
        List<LeaveApplication> pendingRequests =
                applicationRepository.findByCurrentApproverIdAndStatus(managerId, RequestStatus.PENDING);
        response.setTeamPendingRequestCount(pendingRequests.size());

        List<ManagerDashboardResponse.TeamPendingLeaveDTO> pendingDTOs =
                pendingRequests.stream().map(l -> new ManagerDashboardResponse.TeamPendingLeaveDTO(
                        l.getId(),
                        l.getEmployeeId(),
                        l.getEmployeeName(),
                        l.getLeaveType().getLeaveType(),   // entity → String name
                        l.getReason(),
                        l.getStatus(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getDays().doubleValue(),
                        l.getCreatedAt()
                )).collect(Collectors.toList());
        response.setPendingTeamRequests(pendingDTOs);

        // Team on leave today
        LocalDate today = LocalDate.now();
        List<ManagerDashboardResponse.TeamMemberOnLeaveDTO> onLeave = new ArrayList<>();
        for (Employee m : team) {
            applicationRepository.findByEmployee_EmpIdAndStatus(m.getEmpId(), RequestStatus.APPROVED)
                    .stream()
                    .filter(la -> !today.isBefore(la.getStartDate())
                            && !today.isAfter(la.getEndDate()))
                    .forEach(l -> onLeave.add(
                            new ManagerDashboardResponse.TeamMemberOnLeaveDTO(
                                    m.getEmpId(),
                                    m.getName(),
                                    l.getLeaveType().getLeaveType(),
                                    l.getStartDate(),
                                    l.getEndDate(),
                                    (double) Math.max(0,
                                            ChronoUnit.DAYS.between(today, l.getEndDate()))
                            )));
        }
        response.setTeamOnLeaveToday(onLeave);
        response.setTeamOnLeaveCount(onLeave.size());
        response.setLastUpdated(LocalDateTime.now());
        return response;
    }

    // ═══════════════════════════════════════════════════════════════
    // TEAM ON LEAVE / CALENDAR
    // ═══════════════════════════════════════════════════════════════

    public List<TeamMemberBalance> getTeamMembersOnLeaveToday(String managerId) {
        LocalDate today = LocalDate.now();
        return employeeRepository.findActiveTeamMembers(managerId).stream()
                .filter(m -> applicationRepository
                        .findByEmployee_EmpIdAndStatus(m.getEmpId(), RequestStatus.APPROVED)
                        .stream()
                        .anyMatch(la -> !today.isBefore(la.getStartDate())
                                && !today.isAfter(la.getEndDate())))
                .map(m -> {
                    TeamMemberBalance b = new TeamMemberBalance();
                    b.setEmployeeId(m.getEmpId());
                    b.setEmployeeName(m.getName());
                    return b;
                }).collect(Collectors.toList());
    }

    public Map<String, List<LeaveApplicationResponseDTO>> getTeamLeaveCalendar(String managerId) {

        Map<String, List<LeaveApplicationResponseDTO>> cal = new TreeMap<>();

        List<Employee> teamMembers = employeeRepository.findActiveTeamMembers(managerId);

        for (Employee m : teamMembers) {

            // ── 1. Leave records ──────────────────────────────────────────
            List<LeaveApplication> leaves =
                    applicationRepository.findByEmployee_EmpId(m.getEmpId());

            for (LeaveApplication leave : leaves) {
                LeaveApplicationResponseDTO dto = LeaveApplicationMapper.toDTO(leave);
                dto.setEmployeeName(m.getName());   // ← set name so frontend doesn't need extra call
                dto.setIsWfh(false);

                LocalDate d = leave.getStartDate();
                while (!d.isAfter(leave.getEndDate())) {
                    cal.computeIfAbsent(d.toString(), k -> new ArrayList<>()).add(dto);
                    d = d.plusDays(1);
                }
            }

            // ── 2. WFH records (APPROVED + PENDING) ──────────────────────
            List<WfhApplication> wfhList =
                    wfhApplicationRepository.findByEmployee_EmpIdOrderByCreatedAtDesc(m.getEmpId())
                            .stream()
                            .filter(w -> w.getStatus() == RequestStatus.APPROVED
                                    || w.getStatus() == RequestStatus.PENDING)
                            .collect(Collectors.toList());

            for (WfhApplication wfh : wfhList) {
                LeaveApplicationResponseDTO dto = wfhToCalendarDTO(wfh, m.getName());

                LocalDate d = wfh.getStartDate();
                while (!d.isAfter(wfh.getEndDate())) {
                    cal.computeIfAbsent(d.toString(), k -> new ArrayList<>()).add(dto);
                    d = d.plusDays(1);
                }
            }

            // ── 3. Permissions ────────────────────────────────────────────
            List<Permission> permissions =
                    permissionRepository.findByEmployee_EmpIdOrderByCreatedAtDesc(m.getEmpId());

            for (Permission p : permissions) {
                LeaveApplicationResponseDTO dto = permissionToCalendarDTO(p);
                dto.setEmployeeName(m.getName());
                cal.computeIfAbsent(p.getPermissionDate().toString(),
                        k -> new ArrayList<>()).add(dto);
            }
        }

        return cal;
    }

    public Map<String, List<LeaveApplicationResponseDTO>> getMyLeaveCalendar(String employeeId) {

        Employee employee = employeeRepository.findByEmpId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Map<String, List<LeaveApplicationResponseDTO>> cal = new TreeMap<>();

        // ── 1. Leave records ──────────────────────────────────────────
        List<LeaveApplication> leaves =
                applicationRepository.findByEmployee_EmpId(employeeId);

        for (LeaveApplication leave : leaves) {
            LeaveApplicationResponseDTO dto = LeaveApplicationMapper.toDTO(leave);
            dto.setEmployeeName(employee.getName());
            dto.setIsWfh(false);

            LocalDate d = leave.getStartDate();
            while (!d.isAfter(leave.getEndDate())) {
                cal.computeIfAbsent(d.toString(), k -> new ArrayList<>()).add(dto);
                d = d.plusDays(1);
            }
        }

        // ── 2. WFH records (APPROVED + PENDING) ──────────────────────
        List<WfhApplication> wfhList =
                wfhApplicationRepository.findByEmployee_EmpIdOrderByCreatedAtDesc(employeeId)
                        .stream()
                        .filter(w -> w.getStatus() == RequestStatus.APPROVED
                                || w.getStatus() == RequestStatus.PENDING)
                        .collect(Collectors.toList());

        for (WfhApplication wfh : wfhList) {
            LeaveApplicationResponseDTO dto = wfhToCalendarDTO(wfh, employee.getName());

            LocalDate d = wfh.getStartDate();
            while (!d.isAfter(wfh.getEndDate())) {
                cal.computeIfAbsent(d.toString(), k -> new ArrayList<>()).add(dto);
                d = d.plusDays(1);
            }
        }

        // ── 3. Permissions ────────────────────────────────────────────
        List<Permission> permissions =
                permissionRepository.findByEmployee_EmpIdOrderByCreatedAtDesc(employeeId);

        for (Permission p : permissions) {
            LeaveApplicationResponseDTO dto = permissionToCalendarDTO(p);
            dto.setEmployeeName(employee.getName());
            cal.computeIfAbsent(p.getPermissionDate().toString(),
                    k -> new ArrayList<>()).add(dto);
        }

        return cal;
    }

    private LeaveApplicationResponseDTO wfhToCalendarDTO(WfhApplication wfh, String employeeName) {
        LeaveApplicationResponseDTO dto = new LeaveApplicationResponseDTO();
        dto.setId(wfh.getId());
        dto.setEmployeeId(wfh.getEmployeeId());
        dto.setEmployeeName(employeeName);
        dto.setLeaveTypeName("WFH");
        dto.setIsWfh(true);
        dto.setStartDate(wfh.getStartDate());
        dto.setEndDate(wfh.getEndDate());
        dto.setDays(wfh.getTotalDays());
        dto.setReason(wfh.getReason());
        dto.setStatus(wfh.getStatus());
        dto.setRequiredApprovalLevels(wfh.getRequiredApprovalLevels());
        dto.setCurrentApproverId(wfh.getCurrentApproverId());
        dto.setFirstApproverId(wfh.getFirstApproverId());
        dto.setFirstApproverDecision(wfh.getFirstApproverDecision());
        dto.setFirstApproverDecidedAt(wfh.getFirstApproverDecidedAt());
        dto.setSecondApproverId(wfh.getSecondApproverId());
        dto.setSecondApproverDecision(wfh.getSecondApproverDecision());
        dto.setSecondApproverDecidedAt(wfh.getSecondApproverDecidedAt());
        dto.setCreatedAt(wfh.getCreatedAt());
        return dto;
    }

    private LeaveApplicationResponseDTO permissionToCalendarDTO(Permission p) {
        LeaveApplicationResponseDTO dto = new LeaveApplicationResponseDTO();
        dto.setId((long) p.getId());
        dto.setEmployeeId(p.getEmployee().getEmpId());
        dto.setEmployeeName(p.getEmployee().getName());
        dto.setLeaveTypeName("PERMISSION");
        dto.setStartDate(p.getPermissionDate());
        dto.setEndDate(p.getPermissionDate());
        dto.setStatus(p.getStatus());
        dto.setReason(p.getReason());
        dto.setCreatedAt(p.getCreatedAt());
        // Permission-specific fields
        dto.setStartTime(p.getStartTime());
        dto.setEndTime(p.getEndTime());
        dto.setDurationMinutes(p.getDurationMinutes());
        dto.setFirstApproverId(p.getFirstApproverId());
        dto.setFirstApproverDecision(p.getFirstApproverDecision());
        dto.setSecondApproverId(p.getSecondApproverId());
        dto.setSecondApproverDecision(p.getSecondApproverDecision());
        dto.setRejectionReason(p.getRejectionReason());
        // Attachment
        dto.setAttachmentPath(p.getAttachmentPath());
        dto.setAttachmentOriginalName(p.getAttachmentOriginalName());
        dto.setAttachmentContentType(p.getAttachmentContentType());
        dto.setAttachmentSize(p.getAttachmentSize());
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPANY-WIDE STATS
    // ═══════════════════════════════════════════════════════════════

    public Map<String, Object> getCompanyWideStats(Integer year) {
        Map<String, Object> stats = new HashMap<>();
        List<Employee> active = employeeRepository.findActiveEmployees();
        stats.put("totalEmployees", active.size());

        double totalDays = 0; int totalApproved = 0;
        for (Employee e : active) {
            Double u = applicationRepository.getTotalUsedDays(
                    e.getEmpId(), RequestStatus.APPROVED, year);
            if (u != null) { totalDays += u; totalApproved++; }
        }
        stats.put("totalApprovedLeaves", totalApproved);
        stats.put("totalDaysUsed", totalDays);

        double cfU = 0, cfT = 0;
        for (Employee e : active) {
            CarryForwardBalance cf = carryForwardRepository
                    .findByEmployee_EmpIdAndYear(e.getEmpId(), year).orElse(null);
            if (cf != null) { cfU += cf.getTotalUsed(); cfT += cf.getTotalCarriedForward(); }
        }
        stats.put("carryForwardUtilization", cfT > 0 ? (cfU / cfT * 100) : 0.0);
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY QUERY METHODS
    // ═══════════════════════════════════════════════════════════════

    public Integer getPendingTeamRequestsCount(String managerId) {
        return employeeRepository.findActiveTeamMembers(managerId).stream()
                .mapToInt(m -> applicationRepository
                        .findByEmployee_EmpIdAndStatus(m.getEmpId(), RequestStatus.PENDING)
                        .size())
                .sum();
    }

    public List<LeaveApplication> getPendingTeamRequests(String managerId) {
        List<LeaveApplication> all = new ArrayList<>();
        for (Employee m : employeeRepository.findActiveTeamMembers(managerId)) {
            all.addAll(applicationRepository
                    .findByEmployee_EmpIdAndStatus(m.getEmpId(), RequestStatus.PENDING));
        }
        all.sort(Comparator.comparing(LeaveApplication::getCreatedAt));
        return all;
    }

    public Map<RequestStatus, Long> getLeaveCountsByStatus(String employeeId, Integer year) {
        return applicationRepository.findByEmployee_EmpIdAndYear(employeeId, year).stream()
                .collect(Collectors.groupingBy(LeaveApplication::getStatus, Collectors.counting()));
    }

    public List<Employee> getEmployeesCurrentlyOnLeave() {
        LocalDate today = LocalDate.now();
        return employeeRepository.findActiveEmployees().stream()
                .filter(e -> applicationRepository
                        .findByEmployee_EmpIdAndStatus(e.getEmpId(), RequestStatus.APPROVED)
                        .stream()
                        .anyMatch(la -> !today.isBefore(la.getStartDate())
                                && !today.isAfter(la.getEndDate())))
                .collect(Collectors.toList());
    }

    public List<TeamMemberBalance> getEmployeesWithLowBalance(Integer year, Double threshold) {
        return employeeRepository.findActiveEmployees().stream().map(e -> {
            String empId = e.getEmpId();
            Double a = allocationRepository.getTotalAllocatedDays(empId, year);
            Double u = applicationRepository.getTotalUsedDays(empId, RequestStatus.APPROVED, year);
            if (a == null) a = 0.0; if (u == null) u = 0.0;
            if (a - u >= threshold) return null;
            TeamMemberBalance b = new TeamMemberBalance();
            b.setEmployeeId(empId); b.setEmployeeName(e.getName());
            b.setTotalAllocated(a); b.setTotalUsed(u); b.setTotalRemaining(a - u);
            return b;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<TeamMemberBalance> getEmployeesWithHighLOP(Integer year, Double threshold) {
        return employeeRepository.findActiveEmployees().stream().map(e -> {
            TeamMemberBalance b = new TeamMemberBalance();
            b.setEmployeeId(e.getEmpId()); b.setEmployeeName(e.getName());
            return b;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<TeamMemberBalance> getCarryForwardEligible(Integer year) {
        return employeeRepository.findActiveEmployees().stream().map(e -> {
            String empId = e.getEmpId();
            Double a = allocationRepository.getTotalAllocatedDays(empId, year);
            Double u = applicationRepository.getTotalUsedDays(empId, RequestStatus.APPROVED, year);
            if (a == null) a = 0.0; if (u == null) u = 0.0;
            double bal = a - u; if (bal <= 0) return null;
            TeamMemberBalance b = new TeamMemberBalance();
            b.setEmployeeId(empId); b.setEmployeeName(e.getName());
            b.setTotalAllocated(a); b.setTotalUsed(u);
            b.setTotalRemaining(Math.min(bal, 10.0)); // max carry forward cap
            return b;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<TeamMemberBalance> getEmployeesExceedingMonthlyLimit(Integer year, Integer month) {
        return employeeRepository.findActiveEmployees().stream().map(e -> {
            AnnualLeaveMonthlyBalance bal = annualLeaveMonthlyBalanceRepository
                    .findByEmployeeIdAndYearAndMonth(e.getEmpId(), year, month).orElse(null);
            if (bal == null || bal.getUsedDays() <= bal.getAvailableDays()) return null;
            TeamMemberBalance b = new TeamMemberBalance();
            b.setEmployeeId(e.getEmpId()); b.setEmployeeName(e.getName());
            b.setTotalUsed(bal.getUsedDays());
            return b;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public EmployeeDashboardResponse getEmployeeDashboard(String employeeId) {
        return getDashboard(employeeId);
    }

    // ── DTO conversion helpers ─────────────────────────────────────

    public List<EmployeeSummaryDTO> getEmployeesCurrentlyOnLeaveDTOs() {
        return getEmployeesCurrentlyOnLeave().stream().map(EmployeeSummaryDTO::from).toList();
    }

    public List<ManagerDashboardResponse.TeamPendingLeaveDTO> getPendingTeamRequestDTOs(
            String managerId) {
        return getPendingTeamRequests(managerId).stream().map(l ->
                new ManagerDashboardResponse.TeamPendingLeaveDTO(
                        l.getId(),
                        l.getEmployeeId(),
                        l.getEmployeeName(),
                        l.getLeaveType().getLeaveType(),
                        l.getReason(),
                        l.getStatus(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getDays().doubleValue(),
                        l.getCreatedAt())
        ).toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void processLeavesIntoCalendar(Map<String, List<TeamMemberBalance>> cal,
                                           Employee m, List<LeaveApplication> leaves) {
        for (LeaveApplication l : leaves) {
            LocalDate d = l.getStartDate();
            while (!d.isAfter(l.getEndDate())) {
                addToCalendar(cal, d, m);
                d = d.plusDays(1);
            }
        }
    }

    private void addToCalendar(Map<String, List<TeamMemberBalance>> cal,
                               LocalDate date, Employee m) {
        TeamMemberBalance e = new TeamMemberBalance();
        e.setEmployeeId(m.getEmpId());
        e.setEmployeeName(m.getName());
        cal.computeIfAbsent(date.toString(), k -> new ArrayList<>()).add(e);
    }

    private int safeCount(Integer val) {
        return val != null ? val : 0;
    }
}