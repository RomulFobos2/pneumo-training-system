package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.models.SimulationSession;

import java.util.List;
import java.util.Optional;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {

    List<SimulationSession> findByEmployeeIdOrderByStartedAtDesc(Long employeeId);

    List<SimulationSession> findByEmployeeIdAndScenarioIdAndSessionStatus(
            Long employeeId, Long scenarioId, SimulationSessionStatus status);

    Optional<SimulationSession> findByIdAndEmployeeId(Long id, Long employeeId);

    List<SimulationSession> findAllBySessionStatusNotOrderByStartedAtDesc(SimulationSessionStatus status);
}
