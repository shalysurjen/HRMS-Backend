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
}