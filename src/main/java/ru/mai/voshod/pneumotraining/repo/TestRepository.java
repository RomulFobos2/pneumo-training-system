package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.Test;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    List<Test> findAllByOrderByIdDesc();

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);

    /** Активные тесты для прохождения */
    List<Test> findByIsActiveTrueOrderByTitleAsc();
}
