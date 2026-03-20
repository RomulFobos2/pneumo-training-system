package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Test;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;
import ru.mai.voshod.pneumotraining.repo.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
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
    public Map<String, Object> getDashboardData(LocalDate dateFrom, LocalDate dateTo,
                                                 Long testId, Long employeeId) {
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

        // Период по умолчанию — 30 дней
        LocalDate effectiveTo = dateTo != null ? dateTo : LocalDate.now();
        LocalDate effectiveFrom = dateFrom != null ? dateFrom : effectiveTo.minusDays(29);
        LocalDateTime dtFrom = effectiveFrom.atStartOfDay();
        LocalDateTime dtTo = effectiveTo.plusDays(1).atStartOfDay(); // включительно до конца дня

        // Фильтрация по периоду
        List<TestSession> filteredSessions = allSessions.stream()
                .filter(s -> !s.getStartedAt().isBefore(dtFrom) && s.getStartedAt().isBefore(dtTo))
                .toList();

        // Фильтрация по тесту
        if (testId != null) {
            filteredSessions = filteredSessions.stream()
                    .filter(s -> s.getTest().getId().equals(testId))
                    .toList();
        }

        // Фильтрация по сотруднику
        if (employeeId != null) {
            filteredSessions = filteredSessions.stream()
                    .filter(s -> s.getEmployee().getId().equals(employeeId))
                    .toList();
        }

        data.put("sessionsCount", filteredSessions.size());

        long passedCount = filteredSessions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsPassed()))
                .count();
        double passRate = filteredSessions.isEmpty() ? 0.0
                : (passedCount * 100.0) / filteredSessions.size();
        data.put("passRate", Math.round(passRate * 10.0) / 10.0);

        // === Статистика по тестам ===
        Map<Long, List<TestSession>> byTest = filteredSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getTest().getId()));

        List<Map<String, Object>> testStats = new ArrayList<>();
        byTest.forEach((tid, sessions) -> {
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
        Map<Long, List<TestSession>> byEmployee = filteredSessions.stream()
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
        for (TestSession session : filteredSessions) {
            allAnswers.addAll(testSessionAnswerRepository.findByTestSessionIdOrderByIdAsc(session.getId()));
        }

        Map<Long, List<TestSessionAnswer>> byQuestion = allAnswers.stream()
                .collect(Collectors.groupingBy(a -> a.getTestQuestion().getId()));

        List<Map<String, Object>> hardestQuestions = byQuestion.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
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

        // === Активность по дням ===
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd.MM");
        Map<LocalDate, List<TestSession>> byDay = filteredSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate()));

        List<Map<String, Object>> dailyActivity = new ArrayList<>();
        long totalDays = effectiveFrom.until(effectiveTo.plusDays(1), java.time.temporal.ChronoUnit.DAYS);
        for (int i = 0; i < totalDays; i++) {
            LocalDate day = effectiveFrom.plusDays(i);
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

        // Данные для фильтров
        data.put("allEmployees", employeeRepository.findAllByOrderByLastNameAsc());
        data.put("allTests", testRepository.findByIsActiveTrueOrderByTitleAsc());
        data.put("dateFrom", effectiveFrom);
        data.put("dateTo", effectiveTo);
        data.put("selectedTestId", testId);
        data.put("selectedEmployeeId", employeeId);

        return data;
    }

    @Transactional(readOnly = true)
    public byte[] exportDashboardDocx(LocalDate dateFrom, LocalDate dateTo,
                                       Long testId, Long employeeId) {
        Map<String, Object> data = getDashboardData(dateFrom, dateTo, testId, employeeId);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // === Заголовок ===
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Аналитический отчёт");
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setFontFamily("Times New Roman");

            addEmptyLine(doc);

            // === Период и фильтры ===
            LocalDate from = (LocalDate) data.get("dateFrom");
            LocalDate to = (LocalDate) data.get("dateTo");

            XWPFParagraph periodP = doc.createParagraph();
            XWPFRun periodRun = periodP.createRun();
            periodRun.setFontFamily("Times New Roman");
            periodRun.setFontSize(11);
            periodRun.setText("Период: " + from.format(dateFmt) + " — " + to.format(dateFmt));

            if (testId != null) {
                List<?> allTests = (List<?>) data.get("allTests");
                String testName = allTests.stream()
                        .filter(t -> ((Test) t).getId().equals(testId))
                        .map(t -> ((Test) t).getTitle())
                        .findFirst().orElse("—");
                XWPFParagraph tp = doc.createParagraph();
                XWPFRun tr = tp.createRun();
                tr.setFontFamily("Times New Roman");
                tr.setFontSize(11);
                tr.setText("Тест: " + testName);
            }

            if (employeeId != null) {
                List<?> allEmps = (List<?>) data.get("allEmployees");
                String empName = allEmps.stream()
                        .filter(e -> ((Employee) e).getId().equals(employeeId))
                        .map(e -> ((Employee) e).getFullName())
                        .findFirst().orElse("—");
                XWPFParagraph ep = doc.createParagraph();
                XWPFRun er = ep.createRun();
                er.setFontFamily("Times New Roman");
                er.setFontSize(11);
                er.setText("Сотрудник: " + empName);
            }

            addEmptyLine(doc);

            // === Обзорные метрики ===
            addSectionTitle(doc, "Обзорные метрики");

            XWPFTable metricsTable = doc.createTable(4, 2);
            setTableWidth(metricsTable);
            setCell(metricsTable, 0, 0, "Активных сотрудников");
            setCell(metricsTable, 0, 1, data.get("activeEmployees") + " из " + data.get("totalEmployees"));
            setCell(metricsTable, 1, 0, "Активных тестов");
            setCell(metricsTable, 1, 1, data.get("activeTests") + " из " + data.get("totalTests"));
            setCell(metricsTable, 2, 0, "Сессий за период");
            setCell(metricsTable, 2, 1, String.valueOf(data.get("sessionsCount")));
            setCell(metricsTable, 3, 0, "Процент прохождения");
            setCell(metricsTable, 3, 1, data.get("passRate") + "%");

            addEmptyLine(doc);

            // === Статистика по тестам ===
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> testStats = (List<Map<String, Object>>) data.get("testStats");

            if (!testStats.isEmpty()) {
                addSectionTitle(doc, "Статистика по тестам");
                XWPFTable testTable = doc.createTable(testStats.size() + 1, 4);
                setTableWidth(testTable);
                setCell(testTable, 0, 0, "Тест");
                setCell(testTable, 0, 1, "Прохождений");
                setCell(testTable, 0, 2, "Успешных");
                setCell(testTable, 0, 3, "% прохождения");
                for (int i = 0; i < testStats.size(); i++) {
                    Map<String, Object> stat = testStats.get(i);
                    setCell(testTable, i + 1, 0, String.valueOf(stat.get("title")));
                    setCell(testTable, i + 1, 1, String.valueOf(stat.get("total")));
                    setCell(testTable, i + 1, 2, String.valueOf(stat.get("passed")));
                    setCell(testTable, i + 1, 3, stat.get("passRate") + "%");
                }
                addEmptyLine(doc);
            }

            // === Топ-5 сотрудников ===
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topPerformers = (List<Map<String, Object>>) data.get("topPerformers");

            if (!topPerformers.isEmpty()) {
                addSectionTitle(doc, "Топ-5 сотрудников по среднему баллу");
                XWPFTable empTable = doc.createTable(topPerformers.size() + 1, 4);
                setTableWidth(empTable);
                setCell(empTable, 0, 0, "№");
                setCell(empTable, 0, 1, "ФИО");
                setCell(empTable, 0, 2, "Тестов");
                setCell(empTable, 0, 3, "Средний %");
                for (int i = 0; i < topPerformers.size(); i++) {
                    Map<String, Object> perf = topPerformers.get(i);
                    setCell(empTable, i + 1, 0, String.valueOf(i + 1));
                    setCell(empTable, i + 1, 1, String.valueOf(perf.get("fullName")));
                    setCell(empTable, i + 1, 2, String.valueOf(perf.get("testCount")));
                    setCell(empTable, i + 1, 3, perf.get("avgScore") + "%");
                }
                addEmptyLine(doc);
            }

            // === Топ-5 сложных вопросов ===
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hardestQuestions = (List<Map<String, Object>>) data.get("hardestQuestions");

            if (!hardestQuestions.isEmpty()) {
                addSectionTitle(doc, "Топ-5 сложных вопросов");
                XWPFTable qTable = doc.createTable(hardestQuestions.size() + 1, 4);
                setTableWidth(qTable);
                setCell(qTable, 0, 0, "№");
                setCell(qTable, 0, 1, "Вопрос");
                setCell(qTable, 0, 2, "Ответов");
                setCell(qTable, 0, 3, "% ошибок");
                for (int i = 0; i < hardestQuestions.size(); i++) {
                    Map<String, Object> q = hardestQuestions.get(i);
                    setCell(qTable, i + 1, 0, String.valueOf(i + 1));
                    setCell(qTable, i + 1, 1, q.get("questionText") + " (" + q.get("testTitle") + ")");
                    setCell(qTable, i + 1, 2, String.valueOf(q.get("totalAnswers")));
                    setCell(qTable, i + 1, 3, q.get("wrongPercent") + "%");
                }
                addEmptyLine(doc);
            }

            // === Подвал ===
            XWPFParagraph footer = doc.createParagraph();
            footer.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun footerRun = footer.createRun();
            footerRun.setFontFamily("Times New Roman");
            footerRun.setFontSize(9);
            footerRun.setItalic(true);
            footerRun.setText("Сформировано: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            doc.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации DOCX-отчёта аналитики", e);
            throw new RuntimeException("Ошибка генерации DOCX-отчёта", e);
        }
    }

    private void addSectionTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(13);
        run.setFontFamily("Times New Roman");
    }

    private void addEmptyLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private void setCell(XWPFTable table, int row, int col, String text) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "—");
        run.setFontSize(10);
        run.setFontFamily("Times New Roman");
        if (row == 0) {
            run.setBold(true);
        }
    }

    private void setTableWidth(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(9500));
    }
}
