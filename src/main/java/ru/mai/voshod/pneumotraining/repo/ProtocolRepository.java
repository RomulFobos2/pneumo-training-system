package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.enumeration.ProtocolType;
import ru.mai.voshod.pneumotraining.models.Protocol;

import java.util.Optional;

public interface ProtocolRepository extends JpaRepository<Protocol, Long> {

    Optional<Protocol> findByTypeAndSessionId(ProtocolType type, Long sessionId);
}
