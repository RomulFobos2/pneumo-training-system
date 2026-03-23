package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.SimulationAssignment;

import java.util.List;

public interface SimulationAssignmentRepository extends JpaRepository<SimulationAssignment, Long> {
    List<SimulationAssignment> findAllByOrderByCreatedAtDesc();
}
