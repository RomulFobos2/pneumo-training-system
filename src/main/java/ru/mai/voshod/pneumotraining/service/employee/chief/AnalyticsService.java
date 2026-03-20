package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;
import ru.mai.voshod.pneumotraining.repo.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final TestRepository testRepository;
    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;

    public AnalyticsService(EmployeeRepository employeeRepository,
                            TestRepository testRepository,
                            TestSessionRepository testSessionRepository,
                            TestSessionAnswerRepository testSessionAnswerRepository) {
        this.employeeRepository = employeeRepository;
        this.testRepository = testRepository;
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new HashMap<>();

        // === Обзорные метрики ===
        long totalEmployees = employeeRepository.count();
        long activeEmployees = employeeRepository.countByIsActiveTrue();
        long totalTests = testRepository.count();
        long activeTests = testRepository.findByIsActiveTrueOrderByTitleAsc().size();

        data.put("totalEmployees", totalEmployees);
        data.put("activeEmployees", activeEmployees);
        data.put("totalTests", totalTests);
        data.put("activeTests", activeTests);

        // Все завершённые сессии
        List<TestSession> allSessions = testSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus.IN_PROGRESS);

        // Сессии за 30 дней
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<TestSession> recentSessions = allSessions.stream()
                .filter(s -> s.getStartedAt().isAfter(thirtyDaysAgo))
                .toList();

        data.put("sessionsLast30Days", recentSessions.size());

        long passedLast30Days = recentSessions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsPassed()))
                .count();
        double passRate = recentSessions.isEmpty() ? 0.0
                : (passedLast30Days * 100.0) / recentSessions.size();
        data.put("passRateLast30Days", Math.round(passRate * 10.0) / 10.0);

        // === Статистика по тестам ===
        Map<Long, List<TestSession>> byTest = allSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getTest().getId()));

        List<Map<String, Object>> testStats = new ArrayList<>();
        byTest.forEach((testId, sessions) -> {
            Map<String, Object> stat = new HashMap<>();
            stat.put("title", sessions.get(0).getTest().getTitle());
            stat.put("total", sessions.size());
            long passed = sessions.stream().filter(s -> Boolean.TRUE.equals(s.getIsPassed())).count();
            stat.put("passed", passed);
            stat.put("passRate", sessions.isEmpty() ? 0.0
                    : Math.round((passed * 1000.0) / sessions.size()) / 10.0);
            testStats.add(stat);
        });
        testStats.sort((a, b) -> Integer.compare((int) b.get("total"), (int) a.get("total")));
        data.put("testStats", testStats);

        // === Топ-5 сотрудников по среднему баллу ===
        Map<Long, List<TestSession>> byEmployee = allSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getId()));

        List<Map<String, Object>> topPerformers = byEmployee.entrySet().stream()
                .map(entry -> {
                    List<TestSession> sessions = entry.getValue();
                    Map<String, Object> perf = new HashMap<>();
                    perf.put("fullName", sessions.get(0).getEmployee().getFullName());
                    perf.put("testCount", sessions.size());
                    double avgScore = sessions.stream()
                            .mapToDouble(s -> s.getScorePercent() != null ? s.getScorePercent() : 0.0)
                            .average().orElse(0.0);
                    perf.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
                    return perf;
                })
                .sorted((a, b) -> Double.compare((double) b.get("avgScore"), (double) a.get("avgScore")))
                .limit(5)
                .toList();
        data.put("topPerformers", topPerformers);

        // === Топ-5 сложных вопросов ===
        List<TestSessionAnswer> allAnswers = new ArrayList<>();
        for (TestSession session : allSessions) {
            allAnswers.addAll(testSessionAnswerRepository.findByTestSessionIdOrderByIdAsc(session.getId()));
        }

        Map<Long, List<TestSessionAnswer>> byQuestion = allAnswers.stream()
                .collect(Collectors.groupingBy(a -> a.getTestQuestion().getId()));

        List<Map<String, Object>> hardestQuestions = byQuestion.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2) // минимум 2 ответа для статистики
                .map(entry -> {
                    List<TestSessionAnswer> answers = entry.getValue();
                    Map<String, Object> q = new HashMap<>();
                    q.put("questionText", answers.get(0).getTestQuestion().getQuestionText());
                    q.put("testTitle", answers.get(0).getTestQuestion().getTest().getTitle());
                    q.put("totalAnswers", answers.size());
                    long wrong = answers.stream().filter(a -> !a.isCorrect()).count();
                    q.put("wrongPercent", Math.round((wrong * 1000.0) / answers.size()) / 10.0);
                    return q;
                })
                .sorted((a, b) -> Double.compare((double) b.get("wrongPercent"), (double) a.get("wrongPercent")))
                .limit(5)
                .toList();
        data.put("hardestQuestions", hardestQuestions);

        // === Активность по дням (30 дней) ===
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd.MM");
        Map<LocalDate, List<TestSession>> byDay = recentSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate()));

        List<Map<String, Object>> dailyActivity = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<TestSession> daySessions = byDay.getOrDefault(day, List.of());
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", day.format(dayFmt));
            dayData.put("total", daySessions.size());
            dayData.put("passed", daySessions.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsPassed())).count());
            dayData.put("failed", daySessions.stream()
                    .filter(s -> !Boolean.TRUE.equals(s.getIsPassed())).count());
            dailyActivity.add(dayData);
        }
        data.put("dailyActivity", dailyActivity);

        return data;
    }
}
