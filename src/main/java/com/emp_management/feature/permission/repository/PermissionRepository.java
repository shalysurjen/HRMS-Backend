package com.emp_management.feature.permission.repository;
import org.springframework.data.jpa.repository.Query;
import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.shared.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    // Action Center — all permissions where this person is current approver
    List<Permission> findByCurrentApproverIdAndStatus(
            String currentApproverId, RequestStatus status);

    // Employee's own permission history
    List<Permission> findByEmployee_EmpIdOrderByCreatedAtDesc(String empId);

    @Query("SELECT p FROM Permission p WHERE p.employee.empId = :empId " +
            "AND p.status = 'APPROVED' " +
            "AND p.permissionDate >= :startDate " +
            "AND p.permissionDate <= :endDate")
    List<Permission> findApprovedByEmpIdAndDateRange(
            @Param("empId") String empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}