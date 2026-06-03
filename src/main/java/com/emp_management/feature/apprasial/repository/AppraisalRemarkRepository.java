package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.AppraisalRemark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppraisalRemarkRepository extends JpaRepository<AppraisalRemark, Long> {
    List<AppraisalRemark> findByAppraisal_Id(Long appraisalId);
    List<AppraisalRemark> findByAppraisal_IdAndApproverLevel(Long appraisalId, AppraisalRemark.ApproverLevel level);
}
