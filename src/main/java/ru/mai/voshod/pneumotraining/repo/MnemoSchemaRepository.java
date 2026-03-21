package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.MnemoSchema;

import java.util.List;

public interface MnemoSchemaRepository extends JpaRepository<MnemoSchema, Long> {

    List<MnemoSchema> findAllByOrderByTitleAsc();

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);
}
