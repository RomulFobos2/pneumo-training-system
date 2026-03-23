package ru.mai.voshod.pneumotraining.service.employee.chief;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.mapper.TestSessionMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.*;
import ru.mai.voshod.pneumotraining.service.employee.specialist.TestingService;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class ReportService {

    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final SimulationScenarioRepository simulationScenarioRepository;
    private final EmployeeRepository employeeRepository;
    private final TestRepository testRepository;
    private final TestingService testingService;
    private final ObjectMapper objectMapper;

    public ReportService(TestSessionRepository testSessionRepository,
                         TestSessionAnswerRepository testSessionAnswerRepository,
                         SimulationSessionRepository simulationSessionRepository,
                         SimulationScenarioRepository simulationScenarioRepository,
                         EmployeeRepository employeeRepository,
                         TestRepository testRepository,
                         TestingService testingService) {
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.simulationScenarioRepository = simulationScenarioRepository;
        this.employeeRepository = employeeRepository;
        this.testRepository = testRepository;
        this.testingService = testingService;
        this.objectMapper = new ObjectMapper();
    }

    // ========== Все результаты с фильтрами ==========

    @Transactional(readOnly = true)
    public List<TestSessionDTO> getAllResults(Long employeeId, Long testId,
                                              LocalDate dateFrom, LocalDate dateTo) {
        List<TestSession> sessions = testSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus.IN_PROGRESS);

        Stream<TestSession> stream = sessions.stream();

        if (employeeId != null) {
            stream = stream.filter(s -> s.getEmployee().getId().equals(employeeId));
        }
        if (testId != null) {
            stream = stream.filter(s -> s.getTest().getId().equals(testId));
        }
        if (dateFrom != null) {
            stream = stream.filter(s -> !s.getStartedAt().toLocalDate().isBefore(dateFrom));
        }
        if (dateTo != null) {
            stream = stream.filter(s -> !s.getStartedAt().toLocalDate().isAfter(dateTo));
        }

        return stream.map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestSessionDTO> getSessionResult(Long sessionId) {
        return testSessionRepository.findById(sessionId).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<TestSessionAnswerDTO> getSessionAnswerDetails(Long sessionId) {
        return testingService.getSessionAnswerDetails(sessionId);
    }

    // ========== Журнал ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getJournalData() {
        // Сотрудники: специалисты + операторы
        List<Employee> specialists = employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_SPECIALIST");
        List<Employee> operators = employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_OPERATOR");
        List<Employee> employees = Stream.concat(specialists.stream(), operators.stream())
                .sorted(Comparator.comparing(Employee::getLastName))
                .toList();

        List<Test> tests = testRepository.findAllByOrderByIdDesc();

        // Все завершённые сессии
        List<TestSession> sessions = testSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus.IN_PROGRESS);

        // Группировка: (employeeId, testId) → последняя сессия
        Map<String, Double> scores = new HashMap<>();
        Map<String, TestSession> latestSessions = new HashMap<>();

        for (TestSession s : sessions) {
            String key = s.getEmployee().getId() + "_" + s.getTest().getId();
            TestSession existing = latestSessions.get(key);
            if (existing == null || s.getStartedAt().isAfter(existing.getStartedAt())) {
                latestSessions.put(key, s);
                scores.put(key, s.getScorePercent());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("employees", employees);
        result.put("tests", tests);
        result.put("scores", scores);

        // Проходные баллы для цветовой индикации
        Map<String, Integer> passingScores = new HashMap<>();
        for (Test t : tests) {
            passingScores.put(String.valueOf(t.getId()), t.getPassingScore());
        }
        result.put("passingScores", passingScores);

        return result;
    }

    // ========== Excel: Протокол экзамена ==========

    @Transactional(readOnly = true)
    public byte[] exportExamProtocol(Long testId) {
        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty()) return null;

        Test test = testOpt.get();
        List<TestSession> sessions = testSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus.IN_PROGRESS)
                .stream()
                .filter(s -> s.getTest().getId().equals(testId))
                .toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Протокол экзамена");

            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            // Шапка
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("ПРОТОКОЛ экзаменационного тестирования");
            c0.setCellStyle(boldStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            sheet.createRow(2).createCell(0).setCellValue("Тест: " + test.getTitle());
            sheet.createRow(3).createCell(0).setCellValue(
                    "Дата формирования: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(4).createCell(0).setCellValue("Проходной балл: " + test.getPassingScore() + "%");

            // Таблица
            Row headerRow = sheet.createRow(6);
            String[] headers = {"#", "ФИО", "Должность", "Дата сдачи", "Баллы", "%", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 7;
            for (int i = 0; i < sessions.size(); i++) {
                TestSession s = sessions.get(i);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(s.getEmployee().getFullName());
                row.createCell(2).setCellValue(
                        s.getEmployee().getPosition() != null ? s.getEmployee().getPosition() : "");
                row.createCell(3).setCellValue(s.getStartedAt().format(dtf));
                row.createCell(4).setCellValue(s.getScore() + " / " + s.getTotalScore());
                row.createCell(5).setCellValue(String.format("%.1f%%", s.getScorePercent()));
                row.createCell(6).setCellValue(
                        Boolean.TRUE.equals(s.getIsPassed()) ? "Сдан" : "Не сдан");
            }

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 8000);
            sheet.setColumnWidth(2, 6000);
            sheet.setColumnWidth(3, 5000);
            sheet.setColumnWidth(4, 3000);
            sheet.setColumnWidth(5, 3000);
            sheet.setColumnWidth(6, 3500);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании протокола Excel: {}", e.getMessage(), e);
            return null;
        }
    }

    // ========== Excel: Журнал ==========

    @Transactional(readOnly = true)
    public byte[] exportJournal() {
        Map<String, Object> data = getJournalData();

        @SuppressWarnings("unchecked")
        List<Employee> employees = (List<Employee>) data.get("employees");
        @SuppressWarnings("unchecked")
        List<Test> tests = (List<Test>) data.get("tests");
        @SuppressWarnings("unchecked")
        Map<String, Double> scores = (Map<String, Double>) data.get("scores");
        @SuppressWarnings("unchecked")
        Map<String, Integer> passingScores = (Map<String, Integer>) data.get("passingScores");

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Электронный журнал");

            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            passStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            failStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle emptyStyle = workbook.createCellStyle();
            emptyStyle.setAlignment(HorizontalAlignment.CENTER);

            // Заголовок
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("Электронный журнал аттестации");
            c0.setCellStyle(boldStyle);

            sheet.createRow(1).createCell(0).setCellValue(
                    "Дата формирования: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            // Шапка таблицы
            Row headerRow = sheet.createRow(3);
            Cell fioHeader = headerRow.createCell(0);
            fioHeader.setCellValue("ФИО");
            fioHeader.setCellStyle(headerStyle);

            for (int i = 0; i < tests.size(); i++) {
                Cell cell = headerRow.createCell(i + 1);
                cell.setCellValue(tests.get(i).getTitle());
                cell.setCellStyle(headerStyle);
            }

            // Данные
            for (int e = 0; e < employees.size(); e++) {
                Employee emp = employees.get(e);
                Row row = sheet.createRow(4 + e);
                row.createCell(0).setCellValue(emp.getFullName());

                for (int t = 0; t < tests.size(); t++) {
                    String key = emp.getId() + "_" + tests.get(t).getId();
                    Double score = scores.get(key);
                    Cell cell = row.createCell(t + 1);

                    if (score != null) {
                        cell.setCellValue(String.format("%.1f%%", score));
                        Integer passing = passingScores.get(String.valueOf(tests.get(t).getId()));
                        cell.setCellStyle(passing != null && score >= passing ? passStyle : failStyle);
                    } else {
                        cell.setCellValue("—");
                        cell.setCellStyle(emptyStyle);
                    }
                }
            }

            // Ширина колонок
            sheet.setColumnWidth(0, 8000);
            for (int i = 0; i < tests.size(); i++) {
                sheet.setColumnWidth(i + 1, 5000);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании журнала Excel: {}", e.getMessage(), e);
            return null;
        }
    }

    // ========== Результаты симуляций ==========

    @Transactional(readOnly = true)
    public List<SimulationSessionDTO> getAllSimulationResults(Long employeeId, Long scenarioId,
                                                              LocalDate dateFrom, LocalDate dateTo) {
        List<SimulationSession> sessions = simulationSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(SimulationSessionStatus.IN_PROGRESS);

        Stream<SimulationSession> stream = sessions.stream();

        if (employeeId != null) {
            stream = stream.filter(s -> s.getEmployee().getId().equals(employeeId));
        }
        if (scenarioId != null) {
            stream = stream.filter(s -> s.getScenario().getId().equals(scenarioId));
        }
        if (dateFrom != null) {
            stream = stream.filter(s -> !s.getStartedAt().toLocalDate().isBefore(dateFrom));
        }
        if (dateTo != null) {
            stream = stream.filter(s -> !s.getStartedAt().toLocalDate().isAfter(dateTo));
        }

        return stream.map(s -> {
            SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(s);
            dto.setStepResults(s.getStepResults());
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getSimulationSessionResult(Long sessionId) {
        return simulationSessionRepository.findById(sessionId)
                .map(s -> {
                    SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(s);
                    dto.setStepResults(s.getStepResults());
                    dto.setScenarioTitle(s.getScenario().getTitle());
                    return dto;
                });
    }

    public byte[] exportSimulationProtocol(Long scenarioId) {
        List<SimulationSession> sessions = simulationSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(SimulationSessionStatus.IN_PROGRESS)
                .stream()
                .filter(s -> s.getScenario().getId().equals(scenarioId))
                .toList();

        if (sessions.isEmpty()) return null;

        String scenarioTitle = sessions.get(0).getScenario().getTitle();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Протокол симуляции");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("ПРОТОКОЛ прохождения сценария мнемосхемы");
            c0.setCellStyle(boldStyle);

            sheet.createRow(2).createCell(0).setCellValue("Сценарий:");
            sheet.getRow(2).createCell(1).setCellValue(scenarioTitle);
            sheet.createRow(3).createCell(0).setCellValue("Дата формирования:");
            sheet.getRow(3).createCell(1).setCellValue(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            Row headerRow = sheet.createRow(5);
            String[] headers = {"#", "ФИО", "Должность", "Дата", "Шагов пройдено", "Всего шагов", "Статус"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < sessions.size(); i++) {
                SimulationSession s = sessions.get(i);
                Row row = sheet.createRow(6 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(s.getEmployee().getFullName());
                row.createCell(2).setCellValue(s.getEmployee().getPosition());
                row.createCell(3).setCellValue(s.getStartedAt().format(dtf));
                row.createCell(4).setCellValue(s.getCompletedSteps());
                row.createCell(5).setCellValue(s.getTotalSteps());
                row.createCell(6).setCellValue(s.getSessionStatus().getDisplayName());
            }

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 8000);
            sheet.setColumnWidth(2, 6000);
            sheet.setColumnWidth(3, 5000);
            sheet.setColumnWidth(4, 5000);
            sheet.setColumnWidth(5, 4000);
            sheet.setColumnWidth(6, 4000);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании протокола симуляции: {}", e.getMessage(), e);
            return null;
        }
    }

    public byte[] exportSimulationResultToExcel(Long sessionId) {
        Optional<SimulationSession> sessionOpt = simulationSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) return null;

        SimulationSession session = sessionOpt.get();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Результат симуляции");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("Результат прохождения симуляции мнемосхемы");
            c0.setCellStyle(boldStyle);

            sheet.createRow(2).createCell(0).setCellValue("Сотрудник:");
            sheet.getRow(2).createCell(1).setCellValue(session.getEmployee().getFullName());
            sheet.createRow(3).createCell(0).setCellValue("Сценарий:");
            sheet.getRow(3).createCell(1).setCellValue(session.getScenario().getTitle());
            sheet.createRow(4).createCell(0).setCellValue("Дата:");
            sheet.getRow(4).createCell(1).setCellValue(session.getStartedAt().format(dtf));
            sheet.createRow(5).createCell(0).setCellValue("Статус:");
            sheet.getRow(5).createCell(1).setCellValue(session.getSessionStatus().getDisplayName());
            sheet.createRow(6).createCell(0).setCellValue("Пройдено шагов:");
            sheet.getRow(6).createCell(1).setCellValue(
                    session.getCompletedSteps() + " из " + session.getTotalSteps());

            Row headerRow = sheet.createRow(8);
            String[] headers = {"#", "Инструкция", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            if (session.getStepResults() != null && !session.getStepResults().isBlank()) {
                List<Map<String, Object>> stepResults = objectMapper.readValue(
                        session.getStepResults(), new TypeReference<>() {});
                for (int i = 0; i < stepResults.size(); i++) {
                    Map<String, Object> sr = stepResults.get(i);
                    Row row = sheet.createRow(9 + i);
                    row.createCell(0).setCellValue(((Number) sr.get("step")).intValue());
                    Cell instrCell = row.createCell(1);
                    instrCell.setCellValue((String) sr.get("instruction"));
                    instrCell.setCellStyle(wrapStyle);
                    row.createCell(2).setCellValue(
                            Boolean.TRUE.equals(sr.get("passed")) ? "Выполнен" : "Ошибка");
                }
            }

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 12000);
            sheet.setColumnWidth(2, 4000);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании Excel симуляции: {}", e.getMessage(), e);
            return null;
        }
    }

    public byte[] exportSimulationJournal() {
        List<Employee> employees = employeeRepository.findAllByOrderByLastNameAsc().stream()
                .filter(Employee::isActive)
                .toList();
        List<SimulationScenario> scenarios = simulationScenarioRepository.findByIsActiveTrueOrderByTitleAsc();

        List<SimulationSession> allSessions = simulationSessionRepository
                .findAllBySessionStatusNotOrderByStartedAtDesc(SimulationSessionStatus.IN_PROGRESS);

        // Группируем: empId_scenarioId → последняя сессия
        Map<String, SimulationSession> latestByKey = new LinkedHashMap<>();
        for (SimulationSession s : allSessions) {
            String key = s.getEmployee().getId() + "_" + s.getScenario().getId();
            latestByKey.putIfAbsent(key, s); // первая = последняя (сортировка DESC)
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Журнал симуляций");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            CellStyle passStyle = workbook.createCellStyle();
            passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            passStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle failStyle = workbook.createCellStyle();
            failStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            failStyle.setAlignment(HorizontalAlignment.CENTER);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("Журнал прохождения симуляций мнемосхемы");
            c0.setCellStyle(boldStyle);

            // Заголовок: ФИО | Сценарий1 | Сценарий2 | ...
            Row headerRow = sheet.createRow(2);
            Cell fioHeader = headerRow.createCell(0);
            fioHeader.setCellValue("ФИО");
            fioHeader.setCellStyle(headerStyle);
            for (int i = 0; i < scenarios.size(); i++) {
                Cell cell = headerRow.createCell(i + 1);
                cell.setCellValue(scenarios.get(i).getTitle());
                cell.setCellStyle(headerStyle);
            }

            for (int e = 0; e < employees.size(); e++) {
                Employee emp = employees.get(e);
                Row row = sheet.createRow(3 + e);
                row.createCell(0).setCellValue(emp.getFullName());

                for (int s = 0; s < scenarios.size(); s++) {
                    String key = emp.getId() + "_" + scenarios.get(s).getId();
                    SimulationSession session = latestByKey.get(key);
                    Cell cell = row.createCell(s + 1);
                    if (session != null) {
                        String value = session.getCompletedSteps() + "/" + session.getTotalSteps();
                        cell.setCellValue(value);
                        cell.setCellStyle(session.getSessionStatus() == SimulationSessionStatus.COMPLETED
                                ? passStyle : failStyle);
                    } else {
                        cell.setCellValue("—");
                    }
                }
            }

            sheet.setColumnWidth(0, 8000);
            for (int i = 0; i < scenarios.size(); i++) {
                sheet.setColumnWidth(i + 1, 5000);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании журнала симуляций: {}", e.getMessage(), e);
            return null;
        }
    }

    // ========== Вспомогательные ==========

    private TestSessionDTO toDTO(TestSession session) {
        TestSessionDTO dto = TestSessionMapper.INSTANCE.toDTO(session);
        List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
        dto.setQuestionCount(questionIds != null ? questionIds.size() : 0);
        dto.setAnsweredCount((int) testSessionAnswerRepository.countByTestSessionId(session.getId()));
        return dto;
    }

    private List<Long> parseQuestionOrder(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
