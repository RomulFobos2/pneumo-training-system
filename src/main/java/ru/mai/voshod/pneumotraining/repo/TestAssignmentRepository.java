package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.TestAssignment;

import java.util.List;

public interface TestAssignmentRepository extends JpaRepository<TestAssignment, Long> {

    List<TestAssignment> findAllByOrderByCreatedAtDesc();
}
