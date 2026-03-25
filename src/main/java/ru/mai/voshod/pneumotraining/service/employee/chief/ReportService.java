package ru.mai.voshod.pneumotraining.service.employee.chief;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.*;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationAssignmentMapper;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.mapper.TestAssignmentMapper;
import ru.mai.voshod.pneumotraining.mapper.TestSessionMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.*;
import ru.mai.voshod.pneumotraining.service.employee.specialist.TestingService;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ReportService {

    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final SimulationScenarioRepository simulationScenarioRepository;
    private final TestAssignmentRepository testAssignmentRepository;
    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final SimulationAssignmentRepository simulationAssignmentRepository;
    private final SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository;
    private final TestRepository testRepository;
    private final TestingService testingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportService(TestSessionRepository testSessionRepository,
                         TestSessionAnswerRepository testSessionAnswerRepository,
                         SimulationSessionRepository simulationSessionRepository,
                         SimulationScenarioRepository simulationScenarioRepository,
                         EmployeeRepository employeeRepository,
                         TestAssignmentRepository testAssignmentRepository,
                         TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                         SimulationAssignmentRepository simulationAssignmentRepository,
                         SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository,
                         TestRepository testRepository,
                         TestingService testingService) {
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.simulationScenarioRepository = simulationScenarioRepository;
        this.testAssignmentRepository = testAssignmentRepository;
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.simulationAssignmentRepository = simulationAssignmentRepository;
        this.simulationAssignmentEmployeeRepository = simulationAssignmentEmployeeRepository;
        this.testRepository = testRepository;
        this.testingService = testingService;
    }

    @Transactional(readOnly = true)
    public List<TestAssignmentDTO> getAllResults(Long employeeId, Long testId, LocalDate dateFrom, LocalDate dateTo) {
        return testAssignmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> employeeId == null || a.getAssignedEmployees().stream().anyMatch(ae -> ae.getEmployee().getId().equals(employeeId)))
                .filter(a -> testId == null || a.getTest().getId().equals(testId))
                .filter(a -> dateFrom == null || !a.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .filter(a -> dateTo == null || !a.getCreatedAt().toLocalDate().isAfter(dateTo))
                .map(this::toAssignmentResultDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestAssignmentDTO> getAssignmentResult(Long assignmentId) {
        return testAssignmentRepository.findById(assignmentId).map(this::toAssignmentResultDTO);
    }

    @Transactional(readOnly = true)
    public List<TestAssignmentEmployeeDTO> getAssignmentJournal(Long assignmentId) {
        return testAssignmentEmployeeRepository.findByAssignmentId(assignmentId).stream()
                .map(this::toAssignmentEmployeeDTO)
                .sorted(Comparator.comparing(TestAssignmentEmployeeDTO::getEmployeeFullName))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestSessionDTO> getSessionResult(Long sessionId) {
        return testSessionRepository.findById(sessionId).map(this::toSessionDTO);
    }

    @Transactional(readOnly = true)
    public List<TestSessionAnswerDTO> getSessionAnswerDetails(Long sessionId) {
        return testingService.getSessionAnswerDetails(sessionId);
    }

    @Transactional(readOnly = true)
    public byte[] exportExamProtocol(Long testId) {
        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isEmpty()) {
            return null;
        }

        List<TestSession> sessions = testSessionRepository.findAllBySessionStatusNotOrderByStartedAtDesc(TestSessionStatus.IN_PROGRESS)
                .stream()
                .filter(s -> s.getTest().getId().equals(testId))
                .toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Протокол экзамена");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("ПРОТОКОЛ экзаменационного тестирования");
            c0.setCellStyle(boldStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            sheet.createRow(2).createCell(0).setCellValue("Тест: " + testOpt.get().getTitle());
            sheet.createRow(3).createCell(0).setCellValue("Дата формирования: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(4).createCell(0).setCellValue("Проходной балл: " + testOpt.get().getPassingScore() + "%");

            Row headerRow = sheet.createRow(6);
            String[] headers = {"#", "ФИО", "Должность", "Дата сдачи", "Баллы", "%", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < sessions.size(); i++) {
                TestSession s = sessions.get(i);
                Row row = sheet.createRow(7 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(s.getEmployee().getFullName());
                row.createCell(2).setCellValue(s.getEmployee().getPosition() != null ? s.getEmployee().getPosition() : "");
                row.createCell(3).setCellValue(s.getStartedAt().format(dtf));
                row.createCell(4).setCellValue(s.getScore() + " / " + s.getTotalScore());
                row.createCell(5).setCellValue(String.format("%.1f%%", s.getScorePercent()));
                row.createCell(6).setCellValue(Boolean.TRUE.equals(s.getIsPassed()) ? "Сдан" : "Не сдан");
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

    @Transactional(readOnly = true)
    public byte[] exportAssignmentJournal(Long assignmentId) {
        Optional<TestAssignment> assignmentOpt = testAssignmentRepository.findById(assignmentId);
        if (assignmentOpt.isEmpty()) {
            return null;
        }
        List<TestAssignmentEmployeeDTO> journal = getAssignmentJournal(assignmentId);
        return exportTestAssignmentWorkbook(assignmentOpt.get(), journal);
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentDTO> getAllSimulationResults(Long employeeId, Long scenarioId, LocalDate dateFrom, LocalDate dateTo) {
        return simulationAssignmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> employeeId == null || a.getAssignedEmployees().stream().anyMatch(ae -> ae.getEmployee().getId().equals(employeeId)))
                .filter(a -> scenarioId == null || a.getScenario().getId().equals(scenarioId))
                .filter(a -> dateFrom == null || !a.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .filter(a -> dateTo == null || !a.getCreatedAt().toLocalDate().isAfter(dateTo))
                .map(this::toSimulationAssignmentResultDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationAssignmentDTO> getSimulationAssignmentResult(Long assignmentId) {
        return simulationAssignmentRepository.findById(assignmentId).map(this::toSimulationAssignmentResultDTO);
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentEmployeeDTO> getSimulationAssignmentJournal(Long assignmentId) {
        return simulationAssignmentEmployeeRepository.findByAssignmentId(assignmentId).stream()
                .map(this::toSimulationAssignmentEmployeeDTO)
                .sorted(Comparator.comparing(SimulationAssignmentEmployeeDTO::getEmployeeFullName))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getSimulationSessionResult(Long sessionId) {
        return simulationSessionRepository.findById(sessionId).map(s -> {
            SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(s);
            dto.setStepResults(s.getStepResults());
            dto.setScenarioTitle(s.getScenario().getTitle());
            return dto;
        });
    }

    public byte[] exportSimulationProtocol(Long scenarioId) {
        List<SimulationSession> sessions = simulationSessionRepository.findAllBySessionStatusNotOrderByStartedAtDesc(SimulationSessionStatus.IN_PROGRESS)
                .stream()
                .filter(s -> s.getScenario().getId().equals(scenarioId))
                .toList();
        if (sessions.isEmpty()) {
            return null;
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Протокол симуляции");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("ПРОТОКОЛ прохождения сценария мнемосхемы");
            c0.setCellStyle(boldStyle);

            sheet.createRow(2).createCell(0).setCellValue("Сценарий:");
            sheet.getRow(2).createCell(1).setCellValue(sessions.get(0).getScenario().getTitle());
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
        if (sessionOpt.isEmpty()) {
            return null;
        }
        SimulationSession session = sessionOpt.get();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
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
            sheet.getRow(6).createCell(1).setCellValue(session.getCompletedSteps() + " из " + session.getTotalSteps());

            Row headerRow = sheet.createRow(8);
            String[] headers = {"#", "Инструкция", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            if (session.getStepResults() != null && !session.getStepResults().isBlank()) {
                List<Map<String, Object>> stepResults = objectMapper.readValue(session.getStepResults(), new TypeReference<>() {});
                for (int i = 0; i < stepResults.size(); i++) {
                    Map<String, Object> sr = stepResults.get(i);
                    Row row = sheet.createRow(9 + i);
                    row.createCell(0).setCellValue(((Number) sr.get("step")).intValue());
                    Cell instrCell = row.createCell(1);
                    instrCell.setCellValue((String) sr.get("instruction"));
                    instrCell.setCellStyle(wrapStyle);
                    row.createCell(2).setCellValue(Boolean.TRUE.equals(sr.get("passed")) ? "Выполнен" : "Ошибка");
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

    public byte[] exportSimulationAssignmentJournal(Long assignmentId) {
        Optional<SimulationAssignment> assignmentOpt = simulationAssignmentRepository.findById(assignmentId);
        if (assignmentOpt.isEmpty()) {
            return null;
        }
        List<SimulationAssignmentEmployeeDTO> journal = getSimulationAssignmentJournal(assignmentId);
        return exportSimulationAssignmentWorkbook(assignmentOpt.get(), journal);
    }

    private TestAssignmentDTO toAssignmentResultDTO(TestAssignment assignment) {
        TestAssignmentDTO dto = TestAssignmentMapper.INSTANCE.toDTO(assignment);
        fillAssignmentCounters(dto, assignment.getAssignedEmployees());
        return dto;
    }

    private SimulationAssignmentDTO toSimulationAssignmentResultDTO(SimulationAssignment assignment) {
        SimulationAssignmentDTO dto = SimulationAssignmentMapper.INSTANCE.toDTO(assignment);
        fillSimulationCounters(dto, assignment.getAssignedEmployees());
        return dto;
    }

    private void fillAssignmentCounters(TestAssignmentDTO dto, List<TestAssignmentEmployee> employees) {
        dto.setTotalAssigned(employees.size());
        dto.setCompletedCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
        dto.setFailedCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.FAILED).count());
        dto.setOverdueCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
        dto.setFullyCompleted(!employees.isEmpty() && dto.getCompletedCount() == employees.size());
    }

    private void fillSimulationCounters(SimulationAssignmentDTO dto, List<SimulationAssignmentEmployee> employees) {
        dto.setTotalAssigned(employees.size());
        dto.setCompletedCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
        dto.setFailedCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.FAILED).count());
        dto.setOverdueCount((int) employees.stream().filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
        dto.setFullyCompleted(!employees.isEmpty() && dto.getCompletedCount() == employees.size());
    }

    private TestAssignmentEmployeeDTO toAssignmentEmployeeDTO(TestAssignmentEmployee ae) {
        TestAssignmentEmployeeDTO dto = TestAssignmentMapper.INSTANCE.toEmployeeDTO(ae);
        TestSession session = ae.getCompletedSession();
        if (session != null) {
            dto.setStartedAt(session.getStartedAt());
            dto.setFinishedAt(session.getFinishedAt());
            dto.setScore(session.getScore());
            dto.setTotalScore(session.getTotalScore());
            dto.setScorePercent(session.getScorePercent());
            dto.setPassed(session.getIsPassed());
            dto.setSessionStatusName(session.getSessionStatus().name());
            dto.setSessionStatusDisplayName(session.getSessionStatus().getDisplayName());
        }
        return dto;
    }

    private SimulationAssignmentEmployeeDTO toSimulationAssignmentEmployeeDTO(SimulationAssignmentEmployee ae) {
        SimulationAssignmentEmployeeDTO dto = SimulationAssignmentMapper.INSTANCE.toEmployeeDTO(ae);
        SimulationSession session = ae.getCompletedSimulationSession();
        if (session != null) {
            dto.setStartedAt(session.getStartedAt());
            dto.setFinishedAt(session.getFinishedAt());
            dto.setCompletedSteps(session.getCompletedSteps());
            dto.setTotalSteps(session.getTotalSteps());
            dto.setSessionStatus(session.getSessionStatus().name());
            dto.setSessionStatusDisplayName(session.getSessionStatus().getDisplayName());
        }
        return dto;
    }

    private TestSessionDTO toSessionDTO(TestSession session) {
        TestSessionDTO dto = TestSessionMapper.INSTANCE.toDTO(session);
        List<Long> questionIds = parseQuestionOrder(session.getQuestionOrder());
        dto.setQuestionCount(questionIds != null ? questionIds.size() : 0);
        dto.setAnsweredCount((int) testSessionAnswerRepository.countByTestSessionId(session.getId()));
        return dto;
    }

    private List<Long> parseQuestionOrder(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] exportTestAssignmentWorkbook(TestAssignment assignment, List<TestAssignmentEmployeeDTO> journal) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Журнал назначения");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle passStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle failStyle = createFillStyle(workbook, IndexedColors.ROSE);
            CellStyle emptyStyle = workbook.createCellStyle();
            emptyStyle.setAlignment(HorizontalAlignment.CENTER);

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("ЖУРНАЛ ПО НАЗНАЧЕНИЮ ТЕСТА");
            titleCell.setCellStyle(boldStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            sheet.createRow(1).createCell(0).setCellValue("Дата формирования: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(2).createCell(0).setCellValue("Тест: " + assignment.getTest().getTitle());
            sheet.createRow(3).createCell(0).setCellValue("Дедлайн: " + assignment.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(4).createCell(0).setCellValue("Назначено сотрудников: " + journal.size());

            Row headerRow = sheet.createRow(6);
            String[] headers = {"#", "ФИО", "Статус", "Дата завершения", "Баллы", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (int i = 0; i < journal.size(); i++) {
                TestAssignmentEmployeeDTO item = journal.get(i);
                Row row = sheet.createRow(7 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(item.getEmployeeFullName());
                row.createCell(2).setCellValue(item.getStatusDisplayName());
                row.createCell(3).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dtf) : "—");
                row.createCell(4).setCellValue(item.getScore() != null && item.getTotalScore() != null ? item.getScore() + " / " + item.getTotalScore() : "—");

                Cell resultCell = row.createCell(5);
                if (Boolean.TRUE.equals(item.getPassed())) {
                    resultCell.setCellValue(String.format("%.1f%%", item.getScorePercent()));
                    resultCell.setCellStyle(passStyle);
                } else if (item.getScorePercent() != null) {
                    resultCell.setCellValue(String.format("%.1f%%", item.getScorePercent()));
                    resultCell.setCellStyle(failStyle);
                } else {
                    resultCell.setCellValue("—");
                    resultCell.setCellStyle(emptyStyle);
                }
            }

            setJournalColumnWidths(sheet);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании журнала назначения теста: {}", e.getMessage(), e);
            return null;
        }
    }

    private byte[] exportSimulationAssignmentWorkbook(SimulationAssignment assignment, List<SimulationAssignmentEmployeeDTO> journal) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Журнал назначения");
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle passStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle failStyle = createFillStyle(workbook, IndexedColors.ROSE);
            CellStyle emptyStyle = workbook.createCellStyle();
            emptyStyle.setAlignment(HorizontalAlignment.CENTER);

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("ЖУРНАЛ ПО НАЗНАЧЕНИЮ МНЕМОСХЕМЫ");
            titleCell.setCellStyle(boldStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            sheet.createRow(1).createCell(0).setCellValue("Дата формирования: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(2).createCell(0).setCellValue("Сценарий: " + assignment.getScenario().getTitle());
            sheet.createRow(3).createCell(0).setCellValue("Дедлайн: " + assignment.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            sheet.createRow(4).createCell(0).setCellValue("Назначено сотрудников: " + journal.size());

            Row headerRow = sheet.createRow(6);
            String[] headers = {"#", "ФИО", "Статус", "Дата завершения", "Шаги", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (int i = 0; i < journal.size(); i++) {
                SimulationAssignmentEmployeeDTO item = journal.get(i);
                Row row = sheet.createRow(7 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(item.getEmployeeFullName());
                row.createCell(2).setCellValue(item.getStatusDisplayName());
                row.createCell(3).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dtf) : "—");
                row.createCell(4).setCellValue(item.getCompletedSteps() != null && item.getTotalSteps() != null ? item.getCompletedSteps() + " / " + item.getTotalSteps() : "—");

                Cell resultCell = row.createCell(5);
                if ("COMPLETED".equals(item.getSessionStatus())) {
                    resultCell.setCellValue("Выполнено");
                    resultCell.setCellStyle(passStyle);
                } else if (item.getSessionStatus() != null) {
                    resultCell.setCellValue(item.getSessionStatusDisplayName());
                    resultCell.setCellStyle(failStyle);
                } else {
                    resultCell.setCellValue("—");
                    resultCell.setCellStyle(emptyStyle);
                }
            }

            setJournalColumnWidths(sheet);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании журнала назначения мнемосхемы: {}", e.getMessage(), e);
            return null;
        }
    }

    private void setJournalColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 2000);
        sheet.setColumnWidth(1, 9000);
        sheet.setColumnWidth(2, 4500);
        sheet.setColumnWidth(3, 5000);
        sheet.setColumnWidth(4, 3500);
        sheet.setColumnWidth(5, 4500);
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

    private CellStyle createFillStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
