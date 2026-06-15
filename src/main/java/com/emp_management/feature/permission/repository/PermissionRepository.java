package com.emp_management.feature.permission.repository;

import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.shared.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByCurrentApproverIdAndStatus(
            String currentApproverId, RequestStatus status);

    List<Permission> findByEmployee_EmpIdOrderByCreatedAtDesc(String empId);

    @Query("""
        SELECT p FROM Permission p
        WHERE MONTH(p.createdAt) = :month
          AND YEAR(p.createdAt)  = :year
        ORDER BY p.createdAt ASC
    """)
    List<Permission> findByCreatedAtMonthAndYear(
            @Param("month") int month,
            @Param("year") int year);

    List<Permission> findByPermissionDateBetweenOrderByPermissionDateAsc(
            LocalDate from, LocalDate to);

    List<Permission> findByEmployee_EmpIdInAndPermissionDateBetweenOrderByPermissionDateAsc(
            java.util.List<String> empIds,
            LocalDate from,
            LocalDate to);

    // For AttendanceDetailedService — pass enums as parameters (not string literals)
    @Query("""
        SELECT p FROM Permission p
        WHERE p.employee.empId = :empId
          AND p.status IN :statuses
          AND p.permissionDate >= :fromDate
          AND p.permissionDate <= :toDate
        ORDER BY p.permissionDate ASC
    """)
    List<Permission> findApprovedAndPendingByEmpIdAndDateRange(
            @Param("empId")     String empId,
            @Param("statuses")  List<RequestStatus> statuses,
            @Param("fromDate")  LocalDate fromDate,
            @Param("toDate")    LocalDate toDate);
}
