package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.TestAssignmentEmployee;

import java.time.LocalDate;
import java.util.List;

public interface TestAssignmentEmployeeRepository extends JpaRepository<TestAssignmentEmployee, Long> {

    List<TestAssignmentEmployee> findByEmployeeIdAndStatus(Long employeeId, AssignmentStatus status);
    List<TestAssignmentEmployee> findByEmployeeIdOrderByAssignment_CreatedAtDesc(Long employeeId);

    List<TestAssignmentEmployee> findByAssignmentId(Long assignmentId);

    List<TestAssignmentEmployee> findByEmployeeIdAndAssignment_TestIdAndStatus(Long employeeId, Long testId, AssignmentStatus status);

    List<TestAssignmentEmployee> findByStatusAndAssignment_Deadline(AssignmentStatus status, LocalDate deadline);

    List<TestAssignmentEmployee> findByStatusAndAssignment_DeadlineBefore(AssignmentStatus status, LocalDate deadline);

    @Query("""
            SELECT COUNT(ae) > 0
            FROM TestAssignmentEmployee ae
            WHERE ae.assignment.id = :assignmentId
              AND (ae.completedSession IS NOT NULL OR ae.status <> :pendingStatus)
            """)
    boolean existsAttemptForAssignment(@Param("assignmentId") Long assignmentId,
                                       @Param("pendingStatus") AssignmentStatus pendingStatus);
}
