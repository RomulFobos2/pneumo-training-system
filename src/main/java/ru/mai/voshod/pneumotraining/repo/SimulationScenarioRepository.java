package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.voshod.pneumotraining.enumeration.ScenarioType;
import ru.mai.voshod.pneumotraining.models.SimulationScenario;

import java.util.List;

public interface SimulationScenarioRepository extends JpaRepository<SimulationScenario, Long> {

    List<SimulationScenario> findAllByOrderByTitleAsc();

    List<SimulationScenario> findByAvailableWithoutAssignmentTrueAndScenarioTypeOrderByTitleAsc(ScenarioType scenarioType);

    List<SimulationScenario> findByAvailableWithoutAssignmentFalseAndScenarioTypeOrderByTitleAsc(ScenarioType scenarioType);

    List<SimulationScenario> findBySchemaId(Long schemaId);

    List<SimulationScenario> findByParentScenarioIdOrderByTitleAsc(Long parentScenarioId);

    /** Штатные сценарии без родителя — для выбора в качестве родителя */
    List<SimulationScenario> findByScenarioTypeOrderByTitleAsc(ScenarioType scenarioType);

    @Query("SELECT s FROM SimulationScenario s JOIN s.allowedDepartments d " +
            "WHERE s.availableWithoutAssignment = true " +
            "AND s.scenarioType = ru.mai.voshod.pneumotraining.enumeration.ScenarioType.NORMAL " +
            "AND d.id = :departmentId ORDER BY s.title ASC")
    List<SimulationScenario> findAvailableByDepartmentId(@Param("departmentId") Long departmentId);
}
