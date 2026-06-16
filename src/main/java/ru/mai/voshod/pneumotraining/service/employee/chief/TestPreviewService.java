package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.LearningRecommendationDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.TestAnswerMapper;
import ru.mai.voshod.pneumotraining.models.Test;
import ru.mai.voshod.pneumotraining.models.TestAnswer;
import ru.mai.voshod.pneumotraining.models.TestQuestion;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;
import ru.mai.voshod.pneumotraining.repo.TestAnswerRepository;
import ru.mai.voshod.pneumotraining.repo.TestQuestionRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;
import ru.mai.voshod.pneumotraining.service.employee.chief.preview.TestPreviewAnswer;
import ru.mai.voshod.pneumotraining.service.employee.chief.preview.TestPreviewState;
import ru.mai.voshod.pneumotraining.service.employee.specialist.LearningPathService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.TestScoringService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Пробный прогон теста начальником группы.
 * Состояние живёт в HttpSession ({@link TestPreviewState}) — в БД ничего не пишется.
 */
@Service
@Slf4j
public class TestPreviewService {

    private final TestRepository testRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final TestScoringService testScoringService;
    private final LearningPathService learningPathService;

    public TestPreviewService(TestRepository testRepository,
                              TestQuestionRepository testQuestionRepository,
                              TestAnswerRepository testAnswerRepository,
                              TestScoringService testScoringService,
                              LearningPathService learningPathService) {
        this.testRepository = testRepository;
        this.testQuestionRepository = testQuestionRepository;
        this.testAnswerRepository = testAnswerRepository;
        this.testScoringService = testScoringService;
        this.learningPathService = learningPathService;
    }

    @Transactional(readOnly = true)
    public Optional<TestPreviewState> startPreview(Long testId) {
        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty()) {
            log.error("Пробный прогон: тест id={} не найден", testId);
            return Optional.empty();
        }
        Test test = testOpt.get();
        int sampleSize = TestService.resolveSampleSize(test);
        long total = testQuestionRepository.countByTestId(testId);
        if (total < sampleSize) {
            log.error("Пробный прогон: в тесте id={} только {} вопросов, требуется {}", testId, total, sampleSize);
            return Optional.empty();
        }

        List<TestQuestion> questions = testQuestionRepository.findByTestIdOrderByIdAsc(testId);
        List<Long> ids = questions.stream().map(TestQuestion::getId).collect(Collectors.toList());
        Collections.shuffle(ids);
        if (ids.size() > sampleSize) {
            ids = new ArrayList<>(ids.subList(0, sampleSize));
        }

        TestPreviewState state = new TestPreviewState();
        state.setTestId(test.getId());
        state.setTestTitle(test.getTitle());
        state.setTimeLimit(test.getTimeLimit());
        state.setPassingScore(test.getPassingScore());
        state.setAllowBackNavigation(test.isAllowBackNavigation());
        LocalDateTime now = LocalDateTime.now();
        state.setStartedAt(now);
        state.setEndTime(test.getTimeLimit() != null && test.getTimeLimit() > 0
                ? now.plusMinutes(test.getTimeLimit()) : null);
        state.setQuestionOrder(ids);
        state.setStatus(TestSessionStatus.IN_PROGRESS);

        log.info("Пробный прогон стартован: testId={}, вопросов={}, лимит={} мин",
                testId, ids.size(), test.getTimeLimit());
        return Optional.of(state);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getQuestionForDisplay(TestPreviewState state, int questionIndex) {
        if (state.isFinished() || state.getStatus() != TestSessionStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        if (state.getEndTime() != null && LocalDateTime.now().isAfter(state.getEndTime())) {
            return Optional.empty();
        }
        List<Long> ids = state.getQuestionOrder();
        if (questionIndex < 0 || questionIndex >= ids.size()) {
            return Optional.empty();
        }

        Long questionId = ids.get(questionIndex);
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

        TestPreviewAnswer existing = state.getAnswers().get(questionId);

        Map<String, Object> result = new HashMap<>();
        result.put("questionIndex", questionIndex);
        result.put("totalQuestions", ids.size());
        result.put("questionId", questionId);
        result.put("questionText", question.getQuestionText());
        result.put("questionType", question.getQuestionType().name());
        result.put("questionTypeDisplayName", question.getQuestionType().getDisplayName());
        result.put("answers", TestAnswerMapper.INSTANCE.toDTOList(displayAnswers));
        result.put("endTime", state.getEndTime());
        result.put("allowBackNavigation", state.isAllowBackNavigation());
        result.put("hasExistingAnswer", existing != null);
        result.put("testTitle", state.getTestTitle());

        if (existing != null && state.isAllowBackNavigation()) {
            result.put("existingAnswerText", existing.getAnswerText());
            result.put("existingSelectedIds", existing.getSelectedAnswerIds());
        }
        return Optional.of(result);
    }

    /**
     * Возвращает индекс следующего вопроса, -1 если время вышло, -2 если это был последний.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> submitAnswer(TestPreviewState state, int questionIndex,
                                          Map<String, String[]> params) {
        if (state.isFinished() || state.getStatus() != TestSessionStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        if (state.getEndTime() != null && LocalDateTime.now().isAfter(state.getEndTime())) {
            finishPreview(state, TestSessionStatus.EXPIRED);
            return Optional.of(-1);
        }

        List<Long> ids = state.getQuestionOrder();
        if (questionIndex < 0 || questionIndex >= ids.size()) {
            return Optional.empty();
        }

        Long questionId = ids.get(questionIndex);
        Optional<TestQuestion> questionOpt = testQuestionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            return Optional.empty();
        }
        TestQuestion question = questionOpt.get();

        TestPreviewAnswer existing = state.getAnswers().get(questionId);
        if (existing != null && !state.isAllowBackNavigation()) {
            int nextIdx = questionIndex + 1;
            return Optional.of(nextIdx >= ids.size() ? -2 : nextIdx);
        }

        TestPreviewAnswer pa = existing != null ? existing : new TestPreviewAnswer();
        pa.setQuestionId(questionId);
        pa.getSelectedAnswerIds().clear();
        pa.setAnswerText(null);
        fillAnswer(pa, question, params);

        TestSessionAnswer transientAnswer = toTransient(pa, question);
        List<TestAnswer> questionAnswers = testAnswerRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
        double ratio = testScoringService.calculateAnswerRatio(transientAnswer, question, questionAnswers);
        pa.setEarnedScoreRatio(ratio);
        pa.setCorrect(Double.compare(ratio, 1.0) == 0);

        state.getAnswers().put(questionId, pa);

        int nextIdx = questionIndex + 1;
        if (nextIdx >= ids.size()) {
            return Optional.of(-2);
        }
        return Optional.of(nextIdx);
    }

    @Transactional(readOnly = true)
    public void finishPreview(TestPreviewState state, TestSessionStatus status) {
        if (state.isFinished()) return;

        List<Long> ids = state.getQuestionOrder();
        List<TestQuestion> questions = ids.stream()
                .map(testQuestionRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        List<TestSessionAnswer> transientAnswers = new ArrayList<>();
        for (TestQuestion q : questions) {
            TestPreviewAnswer pa = state.getAnswers().get(q.getId());
            if (pa != null) {
                transientAnswers.add(toTransient(pa, q));
            }
        }

        int score = (int) transientAnswers.stream().filter(TestSessionAnswer::isCorrect).count();
        double percent = testScoringService.calculateWeightedPercent(transientAnswers, questions);

        state.setScore(score);
        state.setTotalScore(ids.size());
        state.setScorePercent(percent);
        int passing = state.getPassingScore() != null ? state.getPassingScore() : 0;
        state.setIsPassed(percent >= passing);
        state.setFinishedAt(LocalDateTime.now());
        state.setStatus(status);
        state.setFinished(true);

        log.info("Пробный прогон завершён: testId={}, score={}/{}, %={}",
                state.getTestId(), score, ids.size(), String.format("%.1f", percent));
    }

    public boolean isExpired(TestPreviewState state) {
        if (state.isFinished()) return false;
        if (state.getStatus() != TestSessionStatus.IN_PROGRESS) return false;
        return state.getEndTime() != null && LocalDateTime.now().isAfter(state.getEndTime());
    }

    public TestSessionDTO buildSessionDTO(TestPreviewState state) {
        TestSessionDTO dto = new TestSessionDTO();
        dto.setStartedAt(state.getStartedAt());
        dto.setFinishedAt(state.getFinishedAt());
        dto.setEndTime(state.getEndTime());
        dto.setScore(state.getScore());
        dto.setTotalScore(state.getTotalScore());
        dto.setScorePercent(state.getScorePercent());
        dto.setIsPassed(state.getIsPassed());
        TestSessionStatus s = state.getStatus();
        dto.setSessionStatusName(s != null ? s.name() : null);
        dto.setSessionStatusDisplayName(s != null ? s.getDisplayName() : null);
        dto.setTestId(state.getTestId());
        dto.setTestTitle(state.getTestTitle());
        dto.setTestPassingScore(state.getPassingScore());
        dto.setAllowBackNavigation(state.isAllowBackNavigation());
        dto.setQuestionCount(state.getQuestionOrder().size());
        dto.setAnsweredCount(state.getAnswers().size());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<TestSessionAnswerDTO> buildResultDetails(TestPreviewState state) {
        List<TestSessionAnswerDTO> result = new ArrayList<>();
        for (Long qid : state.getQuestionOrder()) {
            Optional<TestQuestion> qOpt = testQuestionRepository.findById(qid);
            if (qOpt.isEmpty()) continue;
            TestQuestion question = qOpt.get();
            TestPreviewAnswer pa = state.getAnswers().get(qid);

            TestSessionAnswerDTO dto = new TestSessionAnswerDTO();
            dto.setTestQuestionId(qid);
            dto.setQuestionText(question.getQuestionText());
            dto.setQuestionTypeName(question.getQuestionType().getDisplayName());
            dto.setDifficultyLevel(question.getDifficultyLevel());

            double ratio = pa != null && pa.getEarnedScoreRatio() != null ? pa.getEarnedScoreRatio() : 0.0;
            dto.setCorrect(pa != null && pa.isCorrect());
            dto.setEarnedScoreRatio(ratio);
            dto.setScoreLevelDisplayName(testScoringService.getScoreLevelDisplayName(ratio));

            String answerText = pa != null ? pa.getAnswerText() : null;
            QuestionType qType = question.getQuestionType();

            if (qType == QuestionType.SEQUENCE && answerText != null && !answerText.isBlank()) {
                try {
                    String readable = Arrays.stream(answerText.split(","))
                            .map(String::trim)
                            .map(Long::parseLong)
                            .map(id -> testAnswerRepository.findById(id)
                                    .map(TestAnswer::getAnswerText).orElse("?"))
                            .collect(Collectors.joining(", "));
                    dto.setAnswerText(readable);
                } catch (NumberFormatException ignored) {
                    dto.setAnswerText(answerText);
                }
            } else if (qType == QuestionType.MATCHING && answerText != null && !answerText.isBlank()) {
                String readable = Arrays.stream(answerText.split("\\|\\|\\|"))
                        .map(pair -> {
                            String[] parts = pair.split("=", 2);
                            if (parts.length == 2) {
                                try {
                                    String left = testAnswerRepository.findById(Long.parseLong(parts[0].trim()))
                                            .map(TestAnswer::getAnswerText).orElse("?");
                                    return left + " → " + parts[1].trim();
                                } catch (NumberFormatException e) {
                                    return pair;
                                }
                            }
                            return pair;
                        })
                        .collect(Collectors.joining(", "));
                dto.setAnswerText(readable);
            } else {
                dto.setAnswerText(answerText);
            }

            if (pa != null && !pa.getSelectedAnswerIds().isEmpty()) {
                List<TestAnswer> selected = pa.getSelectedAnswerIds().stream()
                        .map(testAnswerRepository::findById)
                        .flatMap(Optional::stream)
                        .toList();
                dto.setSelectedAnswers(TestAnswerMapper.INSTANCE.toDTOList(selected));
            }

            List<TestAnswer> correctAnswers = testAnswerRepository
                    .findByQuestionIdOrderBySortOrderAsc(qid)
                    .stream()
                    .filter(a -> {
                        QuestionType qt = question.getQuestionType();
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
    public LearningRecommendationDTO buildRecommendations(TestPreviewState state) {
        Optional<Test> testOpt = testRepository.findById(state.getTestId());
        if (testOpt.isEmpty()) {
            return new LearningRecommendationDTO();
        }
        Test test = testOpt.get();
        List<TestSessionAnswer> transientAnswers = new ArrayList<>();
        for (Long qid : state.getQuestionOrder()) {
            TestPreviewAnswer pa = state.getAnswers().get(qid);
            if (pa == null) continue;
            Optional<TestQuestion> qOpt = testQuestionRepository.findById(qid);
            if (qOpt.isEmpty()) continue;
            transientAnswers.add(toTransient(pa, qOpt.get()));
        }
        return learningPathService.buildRecommendations(transientAnswers, test);
    }

    // ===== helpers =====

    private TestSessionAnswer toTransient(TestPreviewAnswer pa, TestQuestion question) {
        TestSessionAnswer sa = new TestSessionAnswer();
        sa.setTestQuestion(question);
        sa.setAnswerText(pa.getAnswerText());
        sa.setEarnedScoreRatio(pa.getEarnedScoreRatio());
        sa.setCorrect(pa.isCorrect());
        if (pa.getSelectedAnswerIds() != null && !pa.getSelectedAnswerIds().isEmpty()) {
            List<TestAnswer> selected = pa.getSelectedAnswerIds().stream()
                    .map(testAnswerRepository::findById)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());
            sa.setSelectedAnswers(selected);
        }
        return sa;
    }

    private void fillAnswer(TestPreviewAnswer pa, TestQuestion question, Map<String, String[]> params) {
        QuestionType type = question.getQuestionType();
        switch (type) {
            case SINGLE_CHOICE: {
                String[] selected = params.get("selectedAnswer");
                if (selected != null && selected.length > 0 && !selected[0].isBlank()) {
                    pa.getSelectedAnswerIds().add(Long.parseLong(selected[0]));
                }
                break;
            }
            case MULTIPLE_CHOICE: {
                String[] selected = params.get("selectedAnswers");
                if (selected != null) {
                    for (String idStr : selected) {
                        if (idStr != null && !idStr.isBlank()) {
                            pa.getSelectedAnswerIds().add(Long.parseLong(idStr));
                        }
                    }
                }
                break;
            }
            case SEQUENCE: {
                String[] order = params.get("sequenceOrder");
                if (order != null && order.length > 0) {
                    pa.setAnswerText(order[0]);
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
                pa.setAnswerText(sb.toString());
                break;
            }
            case OPEN_TEXT: {
                String[] text = params.get("openAnswer");
                if (text != null && text.length > 0) {
                    pa.setAnswerText(text[0]);
                }
                break;
            }
        }
    }
}
