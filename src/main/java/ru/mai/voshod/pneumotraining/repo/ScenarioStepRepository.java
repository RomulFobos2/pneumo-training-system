package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.ScenarioStep;

import java.util.List;

public interface ScenarioStepRepository extends JpaRepository<ScenarioStep, Long> {

    List<ScenarioStep> findByScenarioIdOrderByStepNumberAsc(Long scenarioId);
}
