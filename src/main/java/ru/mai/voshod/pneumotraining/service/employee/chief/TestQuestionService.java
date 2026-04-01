package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.TestAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestQuestionDTO;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.mapper.TestQuestionMapper;
import ru.mai.voshod.pneumotraining.models.Test;
import ru.mai.voshod.pneumotraining.models.TestAnswer;
import ru.mai.voshod.pneumotraining.models.TestQuestion;
import ru.mai.voshod.pneumotraining.repo.TestAnswerRepository;
import ru.mai.voshod.pneumotraining.repo.TestQuestionRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;
import ru.mai.voshod.pneumotraining.repo.TheorySectionRepository;

import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TestQuestionService {

    private final TestQuestionRepository testQuestionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final TestRepository testRepository;
    private final TheorySectionRepository theorySectionRepository;

    public TestQuestionService(TestQuestionRepository testQuestionRepository,
                               TestAnswerRepository testAnswerRepository,
                               TestRepository testRepository,
                               TheorySectionRepository theorySectionRepository) {
        this.testQuestionRepository = testQuestionRepository;
        this.testAnswerRepository = testAnswerRepository;
        this.testRepository = testRepository;
        this.theorySectionRepository = theorySectionRepository;
    }

    private Optional<String> validateAnswers(String questionType, List<TestAnswerDTO> answers) {
        if (answers == null) answers = List.of();

        switch (questionType) {
            case "SINGLE_CHOICE": {
                if (answers.size() < 2)
                    return Optional.of("Для вопроса с одним ответом необходимо минимум 2 варианта");
                long correctCount = answers.stream().filter(TestAnswerDTO::isCorrect).count();
                if (correctCount != 1)
                    return Optional.of("Для вопроса с одним ответом нужно отметить ровно 1 правильный вариант");
                break;
            }
            case "MULTIPLE_CHOICE": {
                if (answers.size() < 2)
                    return Optional.of("Для вопроса с несколькими ответами необходимо минимум 2 варианта");
                long correctCount = answers.stream().filter(TestAnswerDTO::isCorrect).count();
                if (correctCount < 1)
                    return Optional.of("Отметьте хотя бы один правильный вариант");
                break;
            }
            case "SEQUENCE": {
                if (answers.size() < 2)
                    return Optional.of("Для вопроса на очерёдность необходимо минимум 2 элемента");
                break;
            }
            case "MATCHING": {
                if (answers.size() < 2)
                    return Optional.of("Для вопроса на соответствие необходимо минимум 2 пары");
                for (TestAnswerDTO answer : answers) {
                    if (answer.getMatchTarget() == null || answer.getMatchTarget().isBlank()) {
                        return Optional.of("Заполните оба столбца для каждой пары соответствия");
                    }
                }
                break;
            }
            case "OPEN_TEXT": {
                if (answers.isEmpty() || answers.get(0).getAnswerText() == null
                        || answers.get(0).getAnswerText().isBlank()) {
                    return Optional.of("Введите эталонный ответ");
                }
                break;
            }
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<Long> saveQuestion(Long testId, String questionText, Integer difficultyLevel,
                                       String questionTypeName, Long theorySectionId,
                                       List<TestAnswerDTO> answerDTOs) {
        log.info("Создание вопроса для теста id={}", testId);

        Optional<Test> testOptional = testRepository.findById(testId);
        if (testOptional.isEmpty()) {
            log.error("Тест не найден: id={}", testId);
            return Optional.empty();
        }

        Optional<String> validationError = validateAnswers(questionTypeName, answerDTOs);
        if (validationError.isPresent()) {
            log.error("Ошибка валидации вопроса: {}", validationError.get());
            return Optional.empty();
        }

        try {
            QuestionType questionType = QuestionType.valueOf(questionTypeName);

            TestQuestion question = new TestQuestion();
            question.setQuestionText(questionText);
            question.setQuestionType(questionType);
            question.setDifficultyLevel(normalizeDifficulty(difficultyLevel));
            question.setTest(testOptional.get());
            if (theorySectionId != null) {
                question.setTheorySection(theorySectionRepository.findById(theorySectionId).orElse(null));
            }

            testQuestionRepository.save(question);

            if (answerDTOs != null && !answerDTOs.isEmpty()) {
                for (int i = 0; i < answerDTOs.size(); i++) {
                    TestAnswerDTO answerDto = answerDTOs.get(i);
                    if (answerDto.getAnswerText() == null || answerDto.getAnswerText().isBlank()) continue;

                    TestAnswer answer = new TestAnswer();
                    answer.setAnswerText(answerDto.getAnswerText().trim());
                    answer.setCorrect(answerDto.isCorrect());
                    answer.setSortOrder(answerDto.getSortOrder() != null ? answerDto.getSortOrder() : i + 1);
                    answer.setMatchTarget(answerDto.getMatchTarget());
                    answer.setQuestion(question);
                    testAnswerRepository.save(answer);
                }
            }

            log.info("Вопрос создан: id={}, тест id={}", question.getId(), testId);
            return Optional.of(question.getId());
        } catch (IllegalArgumentException e) {
            log.error("Неверный тип вопроса: {}", questionTypeName);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при создании вопроса: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editQuestion(Long questionId, String questionText, Integer difficultyLevel,
                                       String questionTypeName, Long theorySectionId,
                                       List<TestAnswerDTO> answerDTOs) {
        log.info("Редактирование вопроса: id={}", questionId);

        Optional<TestQuestion> questionOptional = testQuestionRepository.findById(questionId);
        if (questionOptional.isEmpty()) {
            log.error("Вопрос не найден: id={}", questionId);
            return Optional.empty();
        }

        Optional<String> validationError = validateAnswers(questionTypeName, answerDTOs);
        if (validationError.isPresent()) {
            log.error("Ошибка валидации вопроса: {}", validationError.get());
            return Optional.empty();
        }

        try {
            QuestionType questionType = QuestionType.valueOf(questionTypeName);
            TestQuestion question = questionOptional.get();

            question.setQuestionText(questionText);
            question.setDifficultyLevel(normalizeDifficulty(difficultyLevel));
            question.setQuestionType(questionType);
            if (theorySectionId != null) {
                question.setTheorySection(theorySectionRepository.findById(theorySectionId).orElse(null));
            } else {
                question.setTheorySection(null);
            }
            testQuestionRepository.save(question);

            List<TestAnswer> oldAnswers = testAnswerRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
            testAnswerRepository.deleteAll(oldAnswers);

            if (answerDTOs != null && !answerDTOs.isEmpty()) {
                for (int i = 0; i < answerDTOs.size(); i++) {
                    TestAnswerDTO answerDto = answerDTOs.get(i);
                    if (answerDto.getAnswerText() == null || answerDto.getAnswerText().isBlank()) continue;

                    TestAnswer answer = new TestAnswer();
                    answer.setAnswerText(answerDto.getAnswerText().trim());
                    answer.setCorrect(answerDto.isCorrect());
                    answer.setSortOrder(answerDto.getSortOrder() != null ? answerDto.getSortOrder() : i + 1);
                    answer.setMatchTarget(answerDto.getMatchTarget());
                    answer.setQuestion(question);
                    testAnswerRepository.save(answer);
                }
            }

            log.info("Вопрос обновлён: id={}", questionId);
            return Optional.of(questionId);
        } catch (IllegalArgumentException e) {
            log.error("Неверный тип вопроса: {}", questionTypeName);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при редактировании вопроса: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteQuestion(Long questionId) {
        log.info("Удаление вопроса: id={}", questionId);

        Optional<TestQuestion> questionOptional = testQuestionRepository.findById(questionId);
        if (questionOptional.isEmpty()) {
            log.error("Вопрос не найден: id={}", questionId);
            return false;
        }

        try {
            testQuestionRepository.deleteById(questionId);
            log.info("Вопрос удалён: id={}", questionId);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении вопроса: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<TestQuestionDTO> getQuestionsByTest(Long testId) {
        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByIdAsc(testId);
        return questions.stream().map(question -> {
            TestQuestionDTO dto = TestQuestionMapper.INSTANCE.toDTO(question);
            dto.setAnswerCount((int) testAnswerRepository.countByQuestionId(question.getId()));
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestQuestionDTO> getQuestionById(Long questionId) {
        return testQuestionRepository.findById(questionId)
                .map(question -> {
                    TestQuestionDTO dto = TestQuestionMapper.INSTANCE.toDTO(question);
                    dto.setAnswerCount((int) testAnswerRepository.countByQuestionId(question.getId()));
                    List<TestAnswer> answers = testAnswerRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
                    dto.setAnswers(ru.mai.voshod.pneumotraining.mapper.TestAnswerMapper.INSTANCE.toDTOList(answers));
                    return dto;
                });
    }

    @Transactional(readOnly = true)
    public Optional<Long> getTestIdByQuestionId(Long questionId) {
        return testQuestionRepository.findById(questionId)
                .map(question -> question.getTest().getId());
    }

    private int normalizeDifficulty(Integer difficultyLevel) {
        if (difficultyLevel == null) {
            return 1;
        }
        return Math.max(1, Math.min(3, difficultyLevel));
    }
}
