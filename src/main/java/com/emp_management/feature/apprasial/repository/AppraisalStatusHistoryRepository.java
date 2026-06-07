package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.AppraisalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppraisalStatusHistoryRepository extends JpaRepository<AppraisalStatusHistory, Long> {
    List<AppraisalStatusHistory> findByAppraisal_IdOrderByChangedAtAsc(Long appraisalId);
}
