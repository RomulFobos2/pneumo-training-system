package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.TheoryMaterial;

import java.util.List;

public interface TheoryMaterialRepository extends JpaRepository<TheoryMaterial, Long> {

    List<TheoryMaterial> findBySectionIdOrderBySortOrderAsc(Long sectionId);

    long countBySectionId(Long sectionId);
}
