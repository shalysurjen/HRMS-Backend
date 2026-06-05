package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.SelfAppraisal;
import com.emp_management.feature.apprasial.enums.AppraisalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SelfAppraisalRepository extends JpaRepository<SelfAppraisal, Long> {
    Optional<SelfAppraisal> findByEmployeeIdAndCycle_Id(String employeeId, Long cycleId);
    List<SelfAppraisal> findByEmployeeId(String employeeId);
    List<SelfAppraisal> findByFirstApproverIdAndStatus(String approverId, AppraisalStatus status);
    List<SelfAppraisal> findByFirstApproverIdAndStatusIn(String approverId, List<AppraisalStatus> statuses);
    List<SelfAppraisal> findByFinalApproverIdAndStatusIn(String approverId, List<AppraisalStatus> statuses);
    List<SelfAppraisal> findByCycle_Id(Long cycleId);

    // FIX: needed for getAllForL1Approver — fetch ALL records for L1 except excluded statuses
    // (so L1 sees submitted, under-review, approved, rejected, published — everything except DRAFT & L1_REJECTED)
    List<SelfAppraisal> findByFirstApproverIdAndStatusNotIn(String approverId, List<AppraisalStatus> excludedStatuses);

    // FIX: needed for getAllForL2Approver — already used in patched service
    List<SelfAppraisal> findByFinalApproverIdAndStatusNotIn(String approverId, List<AppraisalStatus> excludedStatuses);
}
