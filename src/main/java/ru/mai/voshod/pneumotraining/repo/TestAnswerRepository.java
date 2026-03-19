package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.TestAnswer;

import java.util.List;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, Long> {

    List<TestAnswer> findByQuestionIdOrderBySortOrderAsc(Long questionId);

    long countByQuestionId(Long questionId);
}
