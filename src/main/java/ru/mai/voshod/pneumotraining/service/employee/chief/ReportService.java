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
    public byte[] exportDetailedAssignmentJournal(Long assignmentId) {
        Optional<TestAssignment> assignmentOpt = testAssignmentRepository.findById(assignmentId);
        if (assignmentOpt.isEmpty()) {
            return null;
        }
        List<TestAssignmentEmployeeDTO> journal = getAssignmentJournal(assignmentId);
        return exportDetailedTestAssignmentWorkbook(assignmentOpt.get(), journal);
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

    @Transactional(readOnly = true)
    public byte[] exportDetailedSimulationAssignmentJournal(Long assignmentId) {
        Optional<SimulationAssignment> assignmentOpt = simulationAssignmentRepository.findById(assignmentId);
        if (assignmentOpt.isEmpty()) {
            return null;
        }
        List<SimulationAssignmentEmployeeDTO> journal = getSimulationAssignmentJournal(assignmentId);
        return exportDetailedSimulationAssignmentWorkbook(assignmentOpt.get(), journal);
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
            titleCell.setCellValue("ПРОТОКОЛ ПРОВЕДЕНИЯ ТЕСТИРОВАНИЯ");
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

    private byte[] exportDetailedTestAssignmentWorkbook(TestAssignment assignment, List<TestAssignmentEmployeeDTO> journal) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            CellStyle passStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle failStyle = createFillStyle(workbook, IndexedColors.ROSE);
            CellStyle partialStyle = createFillStyle(workbook, IndexedColors.LIGHT_YELLOW);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Sheet summarySheet = workbook.createSheet("Сводка");
            Row title = summarySheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("ПОДРОБНЫЙ ЖУРНАЛ НАЗНАЧЕНИЯ ТЕСТА");
            titleCell.setCellStyle(boldStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            summarySheet.createRow(1).createCell(0).setCellValue("Дата формирования: " + LocalDate.now().format(dateFormatter));
            summarySheet.createRow(2).createCell(0).setCellValue("Название назначения: " + assignment.getAssignmentTitle());
            summarySheet.createRow(3).createCell(0).setCellValue("Тест: " + assignment.getTest().getTitle());
            summarySheet.createRow(4).createCell(0).setCellValue("Дедлайн: " + assignment.getDeadline().format(dateFormatter));
            summarySheet.createRow(5).createCell(0).setCellValue("Назначено сотрудников: " + journal.size());

            Row headerRow = summarySheet.createRow(7);
            String[] headers = {"#", "ФИО", "Статус", "Дата завершения", "Балл", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < journal.size(); i++) {
                TestAssignmentEmployeeDTO item = journal.get(i);
                Row row = summarySheet.createRow(8 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(item.getEmployeeFullName());
                row.createCell(2).setCellValue(item.getStatusDisplayName());
                row.createCell(3).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dateTimeFormatter) : "—");
                row.createCell(4).setCellValue(item.getScore() != null && item.getTotalScore() != null
                        ? item.getScore() + " / " + item.getTotalScore()
                        : "—");

                Cell resultCell = row.createCell(5);
                if (item.getScorePercent() != null) {
                    resultCell.setCellValue(String.format("%.1f%%", item.getScorePercent()));
                    resultCell.setCellStyle(Boolean.TRUE.equals(item.getPassed()) ? passStyle : failStyle);
                } else {
                    resultCell.setCellValue("—");
                }
            }
            setJournalColumnWidths(summarySheet);

            for (int i = 0; i < journal.size(); i++) {
                TestAssignmentEmployeeDTO item = journal.get(i);
                Sheet employeeSheet = workbook.createSheet(buildEmployeeSheetName(i + 1, item.getEmployeeFullName()));

                Row employeeTitle = employeeSheet.createRow(0);
                Cell employeeTitleCell = employeeTitle.createCell(0);
                employeeTitleCell.setCellValue("Подробный журнал сотрудника");
                employeeTitleCell.setCellStyle(boldStyle);

                employeeSheet.createRow(1).createCell(0).setCellValue("ФИО:");
                employeeSheet.getRow(1).createCell(1).setCellValue(item.getEmployeeFullName());
                employeeSheet.createRow(2).createCell(0).setCellValue("Статус назначения:");
                employeeSheet.getRow(2).createCell(1).setCellValue(item.getStatusDisplayName());
                employeeSheet.createRow(3).createCell(0).setCellValue("Дата завершения:");
                employeeSheet.getRow(3).createCell(1).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dateTimeFormatter) : "—");

                if (item.getCompletedSessionId() == null) {
                    employeeSheet.createRow(5).createCell(0).setCellValue("Подробное прохождение отсутствует.");
                    setDetailedTestSheetWidths(employeeSheet);
                    continue;
                }

                Optional<TestSessionDTO> sessionOpt = getSessionResult(item.getCompletedSessionId());
                if (sessionOpt.isEmpty()) {
                    employeeSheet.createRow(5).createCell(0).setCellValue("Сессия прохождения не найдена.");
                    setDetailedTestSheetWidths(employeeSheet);
                    continue;
                }

                TestSessionDTO session = sessionOpt.get();
                List<TestSessionAnswerDTO> details = getSessionAnswerDetails(item.getCompletedSessionId());

                employeeSheet.createRow(4).createCell(0).setCellValue("Полностью верные ответы:");
                employeeSheet.getRow(4).createCell(1).setCellValue(session.getScore() + " / " + session.getTotalScore());
                employeeSheet.createRow(5).createCell(0).setCellValue("Итоговый процент:");
                employeeSheet.getRow(5).createCell(1).setCellValue(String.format("%.1f%%", session.getScorePercent()));
                employeeSheet.createRow(6).createCell(0).setCellValue("Результат:");
                employeeSheet.getRow(6).createCell(1).setCellValue(Boolean.TRUE.equals(session.getIsPassed()) ? "Пройден" : "Не пройден");

                Row detailHeader = employeeSheet.createRow(8);
                String[] detailHeaders = {"#", "Вопрос", "Тип", "Сложность", "Коэффициент", "Ответ сотрудника", "Правильный ответ", "Результат"};
                for (int j = 0; j < detailHeaders.length; j++) {
                    Cell cell = detailHeader.createCell(j);
                    cell.setCellValue(detailHeaders[j]);
                    cell.setCellStyle(headerStyle);
                }

                for (int j = 0; j < details.size(); j++) {
                    TestSessionAnswerDTO detail = details.get(j);
                    Row row = employeeSheet.createRow(9 + j);
                    row.createCell(0).setCellValue(j + 1);

                    Cell questionCell = row.createCell(1);
                    questionCell.setCellValue(detail.getQuestionText());
                    questionCell.setCellStyle(wrapStyle);

                    row.createCell(2).setCellValue(detail.getQuestionTypeName());
                    row.createCell(3).setCellValue(detail.getDifficultyLevel() != null ? detail.getDifficultyLevel() : 1);
                    row.createCell(4).setCellValue(detail.getEarnedScoreRatio() != null ? detail.getEarnedScoreRatio() : 0.0);

                    Cell answerCell = row.createCell(5);
                    answerCell.setCellValue(getUserAnswerText(detail));
                    answerCell.setCellStyle(wrapStyle);

                    Cell correctCell = row.createCell(6);
                    correctCell.setCellValue(getCorrectAnswerText(detail));
                    correctCell.setCellStyle(wrapStyle);

                    Cell resultCell = row.createCell(7);
                    resultCell.setCellValue(detail.getScoreLevelDisplayName());
                    double ratio = detail.getEarnedScoreRatio() != null ? detail.getEarnedScoreRatio() : 0.0;
                    if (ratio >= 1.0) {
                        resultCell.setCellStyle(passStyle);
                    } else if (ratio > 0.0) {
                        resultCell.setCellStyle(partialStyle);
                    } else {
                        resultCell.setCellStyle(failStyle);
                    }
                }

                setDetailedTestSheetWidths(employeeSheet);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании подробного журнала назначения теста: {}", e.getMessage(), e);
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
            titleCell.setCellValue("ПРОТОКОЛ ПРОВЕДЕНИЯ СИМУЛЯЦИИ");
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

    private byte[] exportDetailedSimulationAssignmentWorkbook(SimulationAssignment assignment, List<SimulationAssignmentEmployeeDTO> journal) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            CellStyle passStyle = createFillStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle failStyle = createFillStyle(workbook, IndexedColors.ROSE);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Sheet summarySheet = workbook.createSheet("Сводка");
            Row title = summarySheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("ПОДРОБНЫЙ ЖУРНАЛ НАЗНАЧЕНИЯ МНЕМОСХЕМЫ");
            titleCell.setCellStyle(boldStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            summarySheet.createRow(1).createCell(0).setCellValue("Дата формирования: " + LocalDate.now().format(dateFormatter));
            summarySheet.createRow(2).createCell(0).setCellValue("Название назначения: " + assignment.getAssignmentTitle());
            summarySheet.createRow(3).createCell(0).setCellValue("Сценарий: " + assignment.getScenario().getTitle());
            summarySheet.createRow(4).createCell(0).setCellValue("Дедлайн: " + assignment.getDeadline().format(dateFormatter));
            summarySheet.createRow(5).createCell(0).setCellValue("Назначено сотрудников: " + journal.size());

            Row headerRow = summarySheet.createRow(7);
            String[] headers = {"#", "ФИО", "Статус", "Дата завершения", "Шаги", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < journal.size(); i++) {
                SimulationAssignmentEmployeeDTO item = journal.get(i);
                Row row = summarySheet.createRow(8 + i);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(item.getEmployeeFullName());
                row.createCell(2).setCellValue(item.getStatusDisplayName());
                row.createCell(3).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dateTimeFormatter) : "—");
                row.createCell(4).setCellValue(item.getCompletedSteps() != null && item.getTotalSteps() != null
                        ? item.getCompletedSteps() + " / " + item.getTotalSteps()
                        : "—");

                Cell resultCell = row.createCell(5);
                if ("COMPLETED".equals(item.getSessionStatus())) {
                    resultCell.setCellValue("Выполнено");
                    resultCell.setCellStyle(passStyle);
                } else if (item.getSessionStatusDisplayName() != null) {
                    resultCell.setCellValue(item.getSessionStatusDisplayName());
                    resultCell.setCellStyle(failStyle);
                } else {
                    resultCell.setCellValue("—");
                }
            }
            setJournalColumnWidths(summarySheet);

            for (int i = 0; i < journal.size(); i++) {
                SimulationAssignmentEmployeeDTO item = journal.get(i);
                Sheet employeeSheet = workbook.createSheet(buildEmployeeSheetName(i + 1, item.getEmployeeFullName()));

                Row employeeTitle = employeeSheet.createRow(0);
                Cell employeeTitleCell = employeeTitle.createCell(0);
                employeeTitleCell.setCellValue("Подробный журнал сотрудника");
                employeeTitleCell.setCellStyle(boldStyle);

                employeeSheet.createRow(1).createCell(0).setCellValue("ФИО:");
                employeeSheet.getRow(1).createCell(1).setCellValue(item.getEmployeeFullName());
                employeeSheet.createRow(2).createCell(0).setCellValue("Статус назначения:");
                employeeSheet.getRow(2).createCell(1).setCellValue(item.getStatusDisplayName());
                employeeSheet.createRow(3).createCell(0).setCellValue("Дата завершения:");
                employeeSheet.getRow(3).createCell(1).setCellValue(item.getFinishedAt() != null ? item.getFinishedAt().format(dateTimeFormatter) : "—");

                if (item.getCompletedSimulationSessionId() == null) {
                    employeeSheet.createRow(5).createCell(0).setCellValue("Подробное прохождение отсутствует.");
                    setDetailedSimulationSheetWidths(employeeSheet);
                    continue;
                }

                Optional<SimulationSessionDTO> sessionOpt = getSimulationSessionResult(item.getCompletedSimulationSessionId());
                if (sessionOpt.isEmpty()) {
                    employeeSheet.createRow(5).createCell(0).setCellValue("Сессия прохождения не найдена.");
                    setDetailedSimulationSheetWidths(employeeSheet);
                    continue;
                }

                SimulationSessionDTO session = sessionOpt.get();
                employeeSheet.createRow(4).createCell(0).setCellValue("Пройдено шагов:");
                employeeSheet.getRow(4).createCell(1).setCellValue(session.getCompletedSteps() + " / " + session.getTotalSteps());
                employeeSheet.createRow(5).createCell(0).setCellValue("Итоговый статус:");
                employeeSheet.getRow(5).createCell(1).setCellValue(session.getSessionStatusDisplayName());

                List<Map<String, Object>> stepResults = parseStepResults(session.getStepResults());
                if (stepResults.isEmpty()) {
                    employeeSheet.createRow(7).createCell(0).setCellValue("Детализация шагов отсутствует.");
                    setDetailedSimulationSheetWidths(employeeSheet);
                    continue;
                }

                Row detailHeader = employeeSheet.createRow(7);
                String[] detailHeaders = {"#", "Инструкция", "Результат"};
                for (int j = 0; j < detailHeaders.length; j++) {
                    Cell cell = detailHeader.createCell(j);
                    cell.setCellValue(detailHeaders[j]);
                    cell.setCellStyle(headerStyle);
                }

                for (int j = 0; j < stepResults.size(); j++) {
                    Map<String, Object> stepResult = stepResults.get(j);
                    Row row = employeeSheet.createRow(8 + j);
                    row.createCell(0).setCellValue(((Number) stepResult.get("step")).intValue());

                    Cell instructionCell = row.createCell(1);
                    instructionCell.setCellValue((String) stepResult.get("instruction"));
                    instructionCell.setCellStyle(wrapStyle);

                    Cell resultCell = row.createCell(2);
                    boolean passed = Boolean.TRUE.equals(stepResult.get("passed"));
                    resultCell.setCellValue(passed ? "Выполнен" : "Ошибка");
                    resultCell.setCellStyle(passed ? passStyle : failStyle);
                }

                setDetailedSimulationSheetWidths(employeeSheet);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании подробного журнала назначения мнемосхемы: {}", e.getMessage(), e);
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

    private void setDetailedTestSheetWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 2000);
        sheet.setColumnWidth(1, 14000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 3500);
        sheet.setColumnWidth(4, 3500);
        sheet.setColumnWidth(5, 10000);
        sheet.setColumnWidth(6, 10000);
        sheet.setColumnWidth(7, 5000);
    }

    private void setDetailedSimulationSheetWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 2000);
        sheet.setColumnWidth(1, 16000);
        sheet.setColumnWidth(2, 5000);
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

    private CellStyle createWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createFillStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private String buildEmployeeSheetName(int index, String fullName) {
        String normalized = (index + "_" + fullName)
                .replaceAll("[\\\\/?*\\[\\]:]", "_")
                .trim();
        return normalized.length() > 31 ? normalized.substring(0, 31) : normalized;
    }

    private List<Map<String, Object>> parseStepResults(String stepResultsJson) {
        if (stepResultsJson == null || stepResultsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stepResultsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Ошибка при чтении stepResults: {}", e.getMessage());
            return List.of();
        }
    }

    private String getUserAnswerText(TestSessionAnswerDTO detail) {
        if (!detail.getSelectedAnswers().isEmpty()) {
            return detail.getSelectedAnswers().stream()
                    .map(TestAnswerDTO::getAnswerText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
        }
        return detail.getAnswerText() != null && !detail.getAnswerText().isBlank()
                ? detail.getAnswerText()
                : "нет ответа";
    }

    private String getCorrectAnswerText(TestSessionAnswerDTO detail) {
        if (!detail.getCorrectAnswers().isEmpty()) {
            return detail.getCorrectAnswers().stream()
                    .map(TestAnswerDTO::getAnswerText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
        }
        return "—";
    }
}
