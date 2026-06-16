package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.SimulationAssignmentEmployee;

import java.time.LocalDateTime;
import java.util.List;

public interface SimulationAssignmentEmployeeRepository extends JpaRepository<SimulationAssignmentEmployee, Long> {
    List<SimulationAssignmentEmployee> findByAssignmentId(Long assignmentId);
    List<SimulationAssignmentEmployee> findByEmployeeIdAndStatus(Long employeeId, AssignmentStatus status);
    List<SimulationAssignmentEmployee> findByEmployeeIdOrderByAssignment_CreatedAtDesc(Long employeeId);
    List<SimulationAssignmentEmployee> findByEmployeeIdAndAssignment_ScenarioIdAndStatus(Long employeeId, Long scenarioId, AssignmentStatus status);
    List<SimulationAssignmentEmployee> findByStatusAndAssignment_DeadlineBetween(AssignmentStatus status, LocalDateTime from, LocalDateTime to);
    List<SimulationAssignmentEmployee> findByStatusAndAssignment_DeadlineBefore(AssignmentStatus status, LocalDateTime date);

    @Query("SELECT ae.completedSimulationSession.id FROM SimulationAssignmentEmployee ae WHERE ae.employee.id = :employeeId AND ae.completedSimulationSession IS NOT NULL")
    List<Long> findCompletedSimulationSessionIdsByEmployeeId(@Param("employeeId") Long employeeId);
}
