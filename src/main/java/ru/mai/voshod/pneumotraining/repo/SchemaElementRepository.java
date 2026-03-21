package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.SchemaElement;

import java.util.List;

public interface SchemaElementRepository extends JpaRepository<SchemaElement, Long> {

    List<SchemaElement> findBySchemaId(Long schemaId);
}
