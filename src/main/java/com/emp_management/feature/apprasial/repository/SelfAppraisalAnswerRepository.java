package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.SelfAppraisalAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SelfAppraisalAnswerRepository extends JpaRepository<SelfAppraisalAnswer, Long> {
    List<SelfAppraisalAnswer> findByAppraisal_Id(Long appraisalId);

    @Modifying
    @Query("DELETE FROM SelfAppraisalAnswer a WHERE a.appraisal.id = :appraisalId")
    void deleteByAppraisalId(Long appraisalId);
}
