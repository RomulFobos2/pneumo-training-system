package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.TheorySection;

import java.util.List;

public interface TheorySectionRepository extends JpaRepository<TheorySection, Long> {

    List<TheorySection> findAllByOrderBySortOrderAsc();

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);
}
