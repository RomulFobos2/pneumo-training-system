package ru.mai.voshod.pneumotraining.service.employee.specialist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.*;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.TestAnswerMapper;
import ru.mai.voshod.pneumotraining.mapper.TestMapper;
import ru.mai.voshod.pneumotraining.mapper.TestSessionMapper;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.*;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestAssignmentService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Getter
@Slf4j
public class TestingService {

    private final TestRepository testRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;
    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final TestAssignmentService testAssignmentService;
    private final DepartmentService departmentService;
    private final TestScoringService testScoringService;
    private final ObjectMapper objectMapper;

    public TestingService(TestRepository testRepository,
                          TestQuestionRepository testQuestionRepository,
                          TestAnswerRepository testAnswerRepository,
                          TestSessionRepository testSessionRepository,
                          TestSessionAnswerRepository testSessionAnswerRepository,
                          TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                          TestAssignmentService testAssignmentService,
                          DepartmentService departmentService,
                          TestScoringService testScoringService) {
        this.testRepository = testRepository;
        this.testQuestionRepository = testQuestionRepository;
        this.testAnswerRepository = testAnswerRepository;
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.testAssignmentService = testAssignmentService;
        this.departmentService = departmentService;
        this.testScoringService = testScoringService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<TestDTO> getAvailableTests(Employee employee) {
        if (employee.getDepartment() == null) {
            return Collections.emptyList();
        }
        List<Long> ancestorIds = departmentService.getAncestorIds(employee.getDepartment().getId());
        List<Test> tests = testRepository.findAvailableByDepartmentIds(ancestorIds);
        return tests.stream().map(test -> {
            TestDTO dto = TestMapper.INSTANCE.toDTO(test);
            dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestDTO> getTestForStart(Long testId, Employee employee) {
        return testRepository.findById(testId)
                .filter(test -> canEmployeeAccessTest(test, employee))
                .map(test -> {
                    TestDTO dto = TestMapper.INSTANCE.toDTO(test);
                    dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
                    return dto;
                });
    }

    @Transactional
    public Optional<Long> startTest(Long testId, Employee employee) {
        log.info("Старт теста id={} для сотрудника id={}", testId, employee.getId());

        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty() || !canEmployeeAccessTest(testOpt.get(), employee)) {
            log.error("Тест не найден или недоступен: id={}", testId);
            return Optional.empty();
        }

        Test test = testOpt.get();
        long questionCount = testQuestionRepository.countByTestId(testId);
        if (questionCount == 0) {
            log.error("Тест не содержит вопросов: id={}", testId);
            return Optional.empty();
        }

        List<TestSession> existingSessions = testSessionRepository
                .findByEmployeeIdAndTestIdAndSessionStatus(employee.getId(), testId, TestSessionStatus.IN_PROGRESS);
        if (!existingSessions.isEmpty()) {
            log.info("Найдена существующая IN_PROGRESS сессия id={}", existingSessions.get(0).getId());
            return Optional.of(existingSessions.get(0).getId());
        }

        try {
            List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderBySortOrderAsc(testId);
            List<Long> questionIds = questions.stream().map(TestQuestion::getId).collect(Collectors.toList());
            Collections.shuffle(questionIds);

            String questionOrderJson = objectMapper.writeValueAsString(questionIds);

            LocalDateTime now = LocalDateTime.now();
            TestSession session = new TestSession();
            session.setStartedAt(now);
            session.setEndTime(test.getTimeLimit() > 0 ? now.plusMinutes(test.getTimeLimit()) : null);
            session.setSessionStatus(TestSessionStatus.IN_PROGRESS);
            session.setQuestionOrder(questionOrderJson);
            session.setEmployee(employee);
            session.setTest(test);

            testSessionRepository.save(session);
            log.info("Сессия создана: id={}, тест id={}", session.getId(), testId);
            return Optional.of(session.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании сессии: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getQuestionForDisplay(Long sessionId, int questionIndex, Employee employee) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            log.error("Сессия не найдена или не принадлежит пользователю: sessionId={}", sessionId);
            return Optional.empty();
        }

        TestSession session = sessionOpt.get();

        if (session.getSessionStatus() != TestSessionStatus.IN_PROGRESS) {
            log.info("Сессия уже завершена: id={}, status={}", sessionId, session.getSessionStatus());
            return Optional.empty();
        }

        if (session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime())) {
            log.info("Время сессии истекло: id={}", sessionId);
            return Optional.empty();
        }

        List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
        if (questionIds == null || questionIndex < 0 || questionIndex >= questionIds.size()) {
            log.error("Неверный индекс вопроса: {} для сессии id={}", questionIndex, sessionId);
            return Optional.empty();
        }

        Long questionId = questionIds.get(questionIndex);
        Optional<TestQuestion> questionOpt = testQuestionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            return Optional.empty();
        }

        TestQuestion question = questionOpt.get();
        List<TestAnswer> answers = testAnswerRepository.findByQuestionIdOrderBySortOrderAsc(questionId);

        List<TestAnswer> displayAnswers = new ArrayList<>(answers);
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE
                || question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            Collections.shuffle(displayAnswers);
        }

        Optional<TestSessionAnswer> existingAnswer = testSessionAnswerRepository
                .findByTestSessionIdAndTestQuestionId(sessionId, questionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("questionIndex", questionIndex);
        result.put("totalQuestions", questionIds.size());
        result.put("questionId", questionId);
        result.put("questionText", question.getQuestionText());
        result.put("questionType", question.getQuestionType().name());
        result.put("questionTypeDisplayName", question.getQuestionType().getDisplayName());
        result.put("answers", TestAnswerMapper.INSTANCE.toDTOList(displayAnswers));
        result.put("endTime", session.getEndTime());
        result.put("allowBackNavigation", session.getTest().isAllowBackNavigation());
        result.put("hasExistingAnswer", existingAnswer.isPresent());
        result.put("testTitle", session.getTest().getTitle());

        if (existingAnswer.isPresent() && session.getTest().isAllowBackNavigation()) {
            TestSessionAnswer sa = existingAnswer.get();
            result.put("existingAnswerText", sa.getAnswerText());
            result.put("existingSelectedIds", sa.getSelectedAnswers().stream()
                    .map(TestAnswer::getId).collect(Collectors.toList()));
        }

        return Optional.of(result);
    }

    @Transactional
    public Optional<Integer> submitAnswer(Long sessionId, int questionIndex, Employee employee,
                                           Map<String, String[]> params) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        TestSession session = sessionOpt.get();
        if (session.getSessionStatus() != TestSessionStatus.IN_PROGRESS) {
            return Optional.empty();
        }

        if (session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime())) {
            finishTest(sessionId, employee, TestSessionStatus.EXPIRED);
            return Optional.of(-1);
        }

        List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
        if (questionIds == null || questionIndex < 0 || questionIndex >= questionIds.size()) {
            return Optional.empty();
        }

        Long questionId = questionIds.get(questionIndex);
        Optional<TestQuestion> questionOpt = testQuestionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            TestQuestion question = questionOpt.get();

            Optional<TestSessionAnswer> existingOpt = testSessionAnswerRepository
                    .findByTestSessionIdAndTestQuestionId(sessionId, questionId);

            TestSessionAnswer sessionAnswer;
            if (existingOpt.isPresent() && session.getTest().isAllowBackNavigation()) {
                sessionAnswer = existingOpt.get();
                sessionAnswer.getSelectedAnswers().clear();
                sessionAnswer.setAnswerText(null);
            } else if (existingOpt.isEmpty()) {
                sessionAnswer = new TestSessionAnswer();
                sessionAnswer.setTestSession(session);
                sessionAnswer.setTestQuestion(question);
            } else {
                return Optional.of(questionIndex + 1);
            }

            fillAnswer(sessionAnswer, question, params);
            List<TestAnswer> questionAnswers = testAnswerRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
            double earnedScoreRatio = testScoringService.calculateAnswerRatio(sessionAnswer, question, questionAnswers);
            sessionAnswer.setEarnedScoreRatio(earnedScoreRatio);
            sessionAnswer.setCorrect(Double.compare(earnedScoreRatio, 1.0) == 0);

            testSessionAnswerRepository.save(sessionAnswer);

            int nextIndex = questionIndex + 1;
            if (nextIndex >= questionIds.size()) {
                return Optional.of(-2);
            }
            return Optional.of(nextIndex);
        } catch (Exception e) {
            log.error("Ошибка при сохранении ответа: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> finishTest(Long sessionId, Employee employee, TestSessionStatus status) {
        log.info("Завершение сессии id={}, status={}", sessionId, status);

        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        TestSession session = sessionOpt.get();

        if (session.getSessionStatus() != TestSessionStatus.IN_PROGRESS) {
            return Optional.of(sessionId);
        }

        try {
            List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
            int totalScore = questionIds != null ? questionIds.size() : 0;
            List<TestQuestion> questions = questionIds == null
                    ? List.of()
                    : questionIds.stream()
                    .map(testQuestionRepository::findById)
                    .flatMap(Optional::stream)
                    .toList();

            List<TestSessionAnswer> answers = testSessionAnswerRepository
                    .findByTestSessionIdOrderByIdAsc(sessionId);
            int score = (int) answers.stream().filter(TestSessionAnswer::isCorrect).count();

            double scorePercent = testScoringService.calculateWeightedPercent(answers, questions);

            session.setScore(score);
            session.setTotalScore(totalScore);
            session.setScorePercent(scorePercent);
            session.setIsPassed(scorePercent >= session.getTest().getPassingScore());
            session.setFinishedAt(LocalDateTime.now());
            session.setSessionStatus(status);
            testSessionRepository.save(session);

            if (session.getIsPassed()) {
                testAssignmentService.markAssignmentCompleted(
                        employee.getId(), session.getTest().getId(), session);
            } else {
                testAssignmentService.markAssignmentFailed(
                        employee.getId(), session.getTest().getId(), session);
            }

            log.info("Сессия завершена: id={}, score={}/{}, %={}, passed={}",
                    sessionId, score, totalScore, String.format("%.1f", scorePercent), session.getIsPassed());
            return Optional.of(sessionId);
        } catch (Exception e) {
            log.error("Ошибка при завершении сессии: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<TestSessionDTO> getSessionResult(Long sessionId, Employee employee) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        TestSession session = sessionOpt.get();
        TestSessionDTO dto = TestSessionMapper.INSTANCE.toDTO(session);
        List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
        dto.setQuestionCount(questionIds != null ? questionIds.size() : 0);
        dto.setAnsweredCount((int) testSessionAnswerRepository.countByTestSessionId(sessionId));
        return Optional.of(dto);
    }

    @Transactional(readOnly = true)
    public List<TestSessionAnswerDTO> getSessionAnswerDetails(Long sessionId) {
        List<TestSessionAnswer> answers = testSessionAnswerRepository.findByTestSessionIdOrderByIdAsc(sessionId);
        List<TestSessionAnswerDTO> result = new ArrayList<>();

        for (TestSessionAnswer sa : answers) {
            TestSessionAnswerDTO dto = new TestSessionAnswerDTO();
            dto.setId(sa.getId());
            dto.setAnswerText(sa.getAnswerText());
            dto.setCorrect(sa.isCorrect());
            dto.setTestQuestionId(sa.getTestQuestion().getId());
            dto.setQuestionText(sa.getTestQuestion().getQuestionText());
            dto.setQuestionTypeName(sa.getTestQuestion().getQuestionType().getDisplayName());
            dto.setDifficultyLevel(sa.getTestQuestion().getDifficultyLevel());
            double earnedScoreRatio = sa.getEarnedScoreRatio() != null ? sa.getEarnedScoreRatio() : (sa.isCorrect() ? 1.0 : 0.0);
            dto.setEarnedScoreRatio(earnedScoreRatio);
            dto.setScoreLevelDisplayName(testScoringService.getScoreLevelDisplayName(earnedScoreRatio));

            QuestionType qType = sa.getTestQuestion().getQuestionType();
            if (qType == QuestionType.SEQUENCE && sa.getAnswerText() != null && !sa.getAnswerText().isBlank()) {
                try {
                    String readable = Arrays.stream(sa.getAnswerText().split(","))
                            .map(String::trim)
                            .map(Long::parseLong)
                            .map(id -> testAnswerRepository.findById(id)
                                    .map(TestAnswer::getAnswerText).orElse("?"))
                            .collect(Collectors.joining(", "));
                    dto.setAnswerText(readable);
                } catch (NumberFormatException ignored) {}
            } else if (qType == QuestionType.MATCHING && sa.getAnswerText() != null && !sa.getAnswerText().isBlank()) {
                String readable = Arrays.stream(sa.getAnswerText().split("\\|\\|\\|"))
                        .map(pair -> {
                            String[] parts = pair.split("=", 2);
                            if (parts.length == 2) {
                                String left = testAnswerRepository.findById(Long.parseLong(parts[0].trim()))
                                        .map(TestAnswer::getAnswerText).orElse("?");
                                return left + " → " + parts[1].trim();
                            }
                            return pair;
                        })
                        .collect(Collectors.joining(", "));
                dto.setAnswerText(readable);
            }

            dto.setSelectedAnswers(TestAnswerMapper.INSTANCE.toDTOList(sa.getSelectedAnswers()));

            List<TestAnswer> correctAnswers = testAnswerRepository
                    .findByQuestionIdOrderBySortOrderAsc(sa.getTestQuestion().getId())
                    .stream()
                    .filter(a -> {
                        QuestionType qt = sa.getTestQuestion().getQuestionType();
                        return qt == QuestionType.SINGLE_CHOICE || qt == QuestionType.MULTIPLE_CHOICE
                                ? a.isCorrect() : true;
                    })
                    .toList();
            dto.setCorrectAnswers(TestAnswerMapper.INSTANCE.toDTOList(correctAnswers));

            result.add(dto);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public boolean isSessionExpired(Long sessionId, Employee employee) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) return true;
        TestSession session = sessionOpt.get();
        if (session.getSessionStatus() != TestSessionStatus.IN_PROGRESS) return true;
        return session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime());
    }

    private List<Long> parseQuestionOrder(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            log.error("Ошибка парсинга questionOrder: {}", e.getMessage());
            return null;
        }
    }

    private void fillAnswer(TestSessionAnswer sa, TestQuestion question, Map<String, String[]> params) {
        QuestionType type = question.getQuestionType();

        switch (type) {
            case SINGLE_CHOICE: {
                String[] selected = params.get("selectedAnswer");
                if (selected != null && selected.length > 0) {
                    testAnswerRepository.findById(Long.parseLong(selected[0]))
                            .ifPresent(a -> sa.getSelectedAnswers().add(a));
                }
                break;
            }
            case MULTIPLE_CHOICE: {
                String[] selected = params.get("selectedAnswers");
                if (selected != null) {
                    for (String idStr : selected) {
                        testAnswerRepository.findById(Long.parseLong(idStr))
                                .ifPresent(a -> sa.getSelectedAnswers().add(a));
                    }
                }
                break;
            }
            case SEQUENCE: {
                String[] order = params.get("sequenceOrder");
                if (order != null && order.length > 0) {
                    sa.setAnswerText(order[0]);
                }
                break;
            }
            case MATCHING: {
                List<TestAnswer> allAnswers = testAnswerRepository
                        .findByQuestionIdOrderBySortOrderAsc(question.getId());
                StringBuilder sb = new StringBuilder();
                for (TestAnswer answer : allAnswers) {
                    String[] val = params.get("match_" + answer.getId());
                    if (val != null && val.length > 0) {
                        if (sb.length() > 0) sb.append("|||");
                        sb.append(answer.getId()).append("=").append(val[0]);
                    }
                }
                sa.setAnswerText(sb.toString());
                break;
            }
            case OPEN_TEXT: {
                String[] text = params.get("openAnswer");
                if (text != null && text.length > 0) {
                    sa.setAnswerText(text[0]);
                }
                break;
            }
        }
    }

    private boolean canEmployeeAccessTest(Test test, Employee employee) {
        if (test.isAvailableWithoutAssignment() && employee.getDepartment() != null) {
            List<Long> ancestorIds = departmentService.getAncestorIds(employee.getDepartment().getId());
            boolean departmentAllowed = test.getAllowedDepartments().stream()
                    .anyMatch(d -> ancestorIds.contains(d.getId()));
            if (departmentAllowed) return true;
        }
        List<TestAssignmentEmployee> pending = testAssignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_TestIdAndStatus(employee.getId(), test.getId(), AssignmentStatus.PENDING);
        return !pending.isEmpty();
    }
}
