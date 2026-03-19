package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;

import java.util.List;
import java.util.Optional;

public interface TestSessionAnswerRepository extends JpaRepository<TestSessionAnswer, Long> {

    List<TestSessionAnswer> findByTestSessionIdOrderByIdAsc(Long testSessionId);

    Optional<TestSessionAnswer> findByTestSessionIdAndTestQuestionId(Long testSessionId, Long testQuestionId);

    long countByTestSessionId(Long testSessionId);
}
