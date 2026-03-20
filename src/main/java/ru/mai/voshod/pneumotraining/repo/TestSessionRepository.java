package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.models.TestSession;

import java.util.List;
import java.util.Optional;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {

    List<TestSession> findByEmployeeIdOrderByStartedAtDesc(Long employeeId);

    List<TestSession> findByEmployeeIdAndTestIdAndSessionStatus(Long employeeId, Long testId, TestSessionStatus status);

    Optional<TestSession> findByIdAndEmployeeId(Long id, Long employeeId);

    /** Все сессии кроме указанного статуса (для Chief — исключить IN_PROGRESS) */
    List<TestSession> findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus status);
}
