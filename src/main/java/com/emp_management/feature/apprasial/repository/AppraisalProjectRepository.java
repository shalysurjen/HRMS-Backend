package com.emp_management.feature.apprasial.repository;

import com.emp_management.feature.apprasial.entity.AppraisalProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppraisalProjectRepository extends JpaRepository<AppraisalProject, Long> {

    List<AppraisalProject> findByAppraisal_IdAndQuestion_Id(Long appraisalId, Long questionId);

    List<AppraisalProject> findByAppraisal_Id(Long appraisalId);

    void deleteByAppraisal_IdAndQuestion_Id(Long appraisalId, Long questionId);
}