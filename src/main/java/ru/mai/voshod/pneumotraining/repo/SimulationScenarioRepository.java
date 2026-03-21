package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.voshod.pneumotraining.models.SimulationScenario;

import java.util.List;

public interface SimulationScenarioRepository extends JpaRepository<SimulationScenario, Long> {

    List<SimulationScenario> findAllByOrderByTitleAsc();

    List<SimulationScenario> findByIsActiveTrueOrderByTitleAsc();

    List<SimulationScenario> findBySchemaId(Long schemaId);

    @Query("SELECT s FROM SimulationScenario s JOIN s.allowedDepartments d " +
            "WHERE s.isActive = true AND d.id = :departmentId ORDER BY s.title ASC")
    List<SimulationScenario> findAvailableByDepartmentId(@Param("departmentId") Long departmentId);
}
