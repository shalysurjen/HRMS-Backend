package com.emp_management.feature.wfh.repository;

import com.emp_management.feature.wfh.entity.WfhApplication;
import com.emp_management.shared.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface WfhApplicationRepository extends JpaRepository<WfhApplication, Long> {

    List<WfhApplication> findByEmployee_EmpIdOrderByCreatedAtDesc(String empId);

    List<WfhApplication> findByCurrentApproverIdAndStatus(String approverId, RequestStatus status);

    @Query("SELECT COALESCE(SUM(w.totalDays), 0) FROM WfhApplication w " +
            "WHERE w.employee.empId = :empId " +
            "AND w.status IN ('APPROVED', 'PENDING')")
    BigDecimal sumTotalDaysByEmployee(@Param("empId") String empId);

    @Query("""
        SELECT w FROM WfhApplication w
        WHERE w.employee.empId = :empId
          AND w.status IN ('PENDING', 'APPROVED')
          AND w.startDate <= :endDate
          AND w.endDate   >= :startDate
    """)
    List<WfhApplication> findOverlappingWfh(
            @Param("empId") String empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT w FROM WfhApplication w
        WHERE w.employee.empId = :empId
          AND w.id != :excludeId
          AND w.status IN ('PENDING', 'APPROVED')
          AND w.startDate <= :endDate
          AND w.endDate   >= :startDate
    """)
    List<WfhApplication> findOverlappingWfhExcluding(
            @Param("empId") String empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    @Query("""
        SELECT w FROM WfhApplication w
        WHERE MONTH(w.createdAt) = :month
          AND YEAR(w.createdAt)  = :year
        ORDER BY w.createdAt ASC
    """)
    List<WfhApplication> findByCreatedAtMonthAndYear(
            @Param("month") int month,
            @Param("year") int year);

    List<WfhApplication> findByStartDateBetweenOrderByStartDateAsc(
            LocalDate from, LocalDate to);

    // Existing — used by LeaveApplicationService
    List<WfhApplication> findByEmployee_EmpIdInAndStartDateBetweenOrderByStartDateAsc(
            java.util.List<String> empIds,
            LocalDate from,
            LocalDate to);

    // For AttendanceDetailedService — pass enums as parameters (not string literals)
    @Query("""
        SELECT w FROM WfhApplication w
        WHERE w.employee.empId = :empId
          AND w.status IN :statuses
          AND w.startDate <= :toDate
          AND w.endDate   >= :fromDate
        ORDER BY w.startDate ASC
    """)
    List<WfhApplication> findApprovedAndPendingByEmpIdAndDateRange(
            @Param("empId")    String empId,
            @Param("statuses") List<RequestStatus> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate")   LocalDate toDate);
}
