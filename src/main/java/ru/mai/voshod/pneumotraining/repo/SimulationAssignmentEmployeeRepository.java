package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.SimulationAssignmentEmployee;

import java.time.LocalDate;
import java.util.List;

public interface SimulationAssignmentEmployeeRepository extends JpaRepository<SimulationAssignmentEmployee, Long> {
    List<SimulationAssignmentEmployee> findByAssignmentId(Long assignmentId);
    List<SimulationAssignmentEmployee> findByEmployeeIdAndStatus(Long employeeId, AssignmentStatus status);
    List<SimulationAssignmentEmployee> findByEmployeeIdOrderByAssignment_CreatedAtDesc(Long employeeId);
    List<SimulationAssignmentEmployee> findByEmployeeIdAndAssignment_ScenarioIdAndStatus(Long employeeId, Long scenarioId, AssignmentStatus status);
    List<SimulationAssignmentEmployee> findByStatusAndAssignment_Deadline(AssignmentStatus status, LocalDate date);
    List<SimulationAssignmentEmployee> findByStatusAndAssignment_DeadlineBefore(AssignmentStatus status, LocalDate date);
}
