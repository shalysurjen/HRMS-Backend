package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.AppraisalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppraisalQuestionRepository extends JpaRepository<AppraisalQuestion, Long> {
    // Ordered by sortOrder only — section grouping order is controlled by SECTION_ORDER in service
    List<AppraisalQuestion> findByCycle_IdAndIsDeletedFalseOrderBySortOrderAsc(Long cycleId);
}