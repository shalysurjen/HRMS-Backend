package com.emp_management.feature.apprasial.repository;
import com.emp_management.feature.apprasial.entity.AppraisalCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppraisalCycleRepository extends JpaRepository<AppraisalCycle, Long> {
    Optional<AppraisalCycle> findByIsActiveTrue();
    List<AppraisalCycle> findAllByOrderByStartYearDesc();
}
