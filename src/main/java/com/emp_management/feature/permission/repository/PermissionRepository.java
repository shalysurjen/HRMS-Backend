package com.emp_management.feature.permission.repository;

import com.emp_management.feature.permission.entity.Permission;
import com.emp_management.shared.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    // Action Center — all permissions where this person is current approver
    List<Permission> findByCurrentApproverIdAndStatus(
            String currentApproverId, RequestStatus status);

    // Employee's own permission history
    List<Permission> findByEmployee_EmpIdOrderByCreatedAtDesc(String empId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM Permission p
        WHERE MONTH(p.createdAt) = :month
          AND YEAR(p.createdAt)  = :year
        ORDER BY p.createdAt ASC
    """)
    List<Permission> findByCreatedAtMonthAndYear(
            @org.springframework.data.repository.query.Param("month") int month,
            @org.springframework.data.repository.query.Param("year") int year);

    List<Permission> findByPermissionDateBetweenOrderByPermissionDateAsc(
            java.time.LocalDate from, java.time.LocalDate to);

    List<Permission> findByEmployee_EmpIdInAndPermissionDateBetweenOrderByPermissionDateAsc(
            java.util.List<String> empIds,
            java.time.LocalDate from,
            java.time.LocalDate to);
}