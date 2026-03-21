package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.SchemaConnection;

import java.util.List;

public interface SchemaConnectionRepository extends JpaRepository<SchemaConnection, Long> {

    List<SchemaConnection> findBySchemaId(Long schemaId);
}
