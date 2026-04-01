package ru.mai.voshod.pneumotraining.service.employee.specialist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.mapper.TestSessionMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.SimulationSession;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.repo.SimulationSessionRepository;
import ru.mai.voshod.pneumotraining.repo.TestAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.TestSessionAnswerRepository;
import ru.mai.voshod.pneumotraining.repo.TestSessionRepository;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class ResultService {

    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;
    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final TestingService testingService;
    private final ObjectMapper objectMapper;

    public ResultService(TestSessionRepository testSessionRepository,
                         TestSessionAnswerRepository testSessionAnswerRepository,
                         TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                         SimulationSessionRepository simulationSessionRepository,
                         TestingService testingService) {
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.testingService = testingService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<TestSessionDTO> getMyResults(Employee employee) {
        List<TestSession> sessions = testSessionRepository.findByEmployeeIdOrderByStartedAtDesc(employee.getId());
        List<Long> assignmentSessionIds = testAssignmentEmployeeRepository.findCompletedSessionIdsByEmployeeId(employee.getId());
        return sessions.stream()
                .filter(s -> s.getSessionStatus() != TestSessionStatus.IN_PROGRESS)
                .map(s -> {
                    TestSessionDTO dto = toDTO(s);
                    dto.setHasAssignment(assignmentSessionIds.contains(s.getId()));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestSessionDTO> getSessionResult(Long sessionId, Employee employee) {
        return testingService.getSessionResult(sessionId, employee);
    }

    @Transactional(readOnly = true)
    public List<TestSessionAnswerDTO> getSessionAnswerDetails(Long sessionId, Employee employee) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return List.of();
        }
        return testingService.getSessionAnswerDetails(sessionId);
    }

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getSimulationResult(Long sessionId, Employee employee) {
        return simulationSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId())
                .map(session -> {
                    SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(session);
                    dto.setStepResults(session.getStepResults());
                    dto.setScenarioTitle(session.getScenario().getTitle());
                    return dto;
                });
    }

    public byte[] exportSessionToExcel(Long sessionId, Employee employee) {
        Optional<TestSessionDTO> sessionOpt = getSessionResult(sessionId, employee);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        TestSessionDTO session = sessionOpt.get();
        List<TestSessionAnswerDTO> details = getSessionAnswerDetails(sessionId, employee);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Результат тестирования");

            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Отчёт по результатам тестирования");
            titleCell.setCellStyle(boldStyle);

            sheet.createRow(2).createCell(0).setCellValue("Сотрудник:");
            sheet.getRow(2).createCell(1).setCellValue(session.getEmployeeFullName());

            sheet.createRow(3).createCell(0).setCellValue("Тест:");
            sheet.getRow(3).createCell(1).setCellValue(session.getTestTitle());

            sheet.createRow(4).createCell(0).setCellValue("Дата:");
            sheet.getRow(4).createCell(1).setCellValue(session.getStartedAt().format(dtf));

            sheet.createRow(5).createCell(0).setCellValue("Полностью верные ответы:");
            sheet.getRow(5).createCell(1).setCellValue(session.getScore() + " / " + session.getTotalScore());

            sheet.createRow(6).createCell(0).setCellValue("Итоговый процент:");
            sheet.getRow(6).createCell(1).setCellValue(String.format("%.1f%%", session.getScorePercent()));

            sheet.createRow(7).createCell(0).setCellValue("Примечание:");
            sheet.getRow(7).createCell(1).setCellValue("Процент рассчитан с учетом сложности вопросов");

            sheet.createRow(8).createCell(0).setCellValue("Статус:");
            sheet.getRow(8).createCell(1).setCellValue(
                    Boolean.TRUE.equals(session.getIsPassed()) ? "Пройден" : "Не пройден");

            Row headerRow = sheet.createRow(10);
            String[] headers = {"#", "Вопрос", "Тип", "Сложность", "Коэффициент", "Ваш ответ", "Правильный ответ", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < details.size(); i++) {
                TestSessionAnswerDTO detail = details.get(i);
                Row row = sheet.createRow(11 + i);
                row.createCell(0).setCellValue(i + 1);

                Cell questionCell = row.createCell(1);
                questionCell.setCellValue(detail.getQuestionText());
                questionCell.setCellStyle(wrapStyle);

                row.createCell(2).setCellValue(detail.getQuestionTypeName());
                row.createCell(3).setCellValue(detail.getDifficultyLevel() != null ? detail.getDifficultyLevel() : 1);
                row.createCell(4).setCellValue(detail.getEarnedScoreRatio() != null ? detail.getEarnedScoreRatio() : 0.0);

                Cell userCell = row.createCell(5);
                userCell.setCellValue(getUserAnswerText(detail));
                userCell.setCellStyle(wrapStyle);

                Cell correctCell = row.createCell(6);
                correctCell.setCellValue(getCorrectAnswerText(detail));
                correctCell.setCellStyle(wrapStyle);

                row.createCell(7).setCellValue(detail.getScoreLevelDisplayName());
            }

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 10000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 3000);
            sheet.setColumnWidth(4, 3500);
            sheet.setColumnWidth(5, 8000);
            sheet.setColumnWidth(6, 8000);
            sheet.setColumnWidth(7, 4500);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка при создании Excel: {}", e.getMessage(), e);
            return null;
        }
    }

    public byte[] exportSimulationToExcel(Long sessionId, Employee employee) {
        Optional<SimulationSession> sessionOpt = simulationSessionRepository
                .findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return null;
        }

        SimulationSession session = sessionOpt.get();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Результат симуляции");

            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Результат прохождения симуляции мнемосхемы");
            titleCell.setCellStyle(boldStyle);

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
                    Map<String, Object> stepResult = stepResults.get(i);
                    Row row = sheet.createRow(9 + i);
                    row.createCell(0).setCellValue(((Number) stepResult.get("step")).intValue());
                    Cell instructionCell = row.createCell(1);
                    instructionCell.setCellValue((String) stepResult.get("instruction"));
                    instructionCell.setCellStyle(wrapStyle);
                    row.createCell(2).setCellValue(
                            Boolean.TRUE.equals(stepResult.get("passed")) ? "Выполнен" : "Ошибка");
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

    private TestSessionDTO toDTO(TestSession session) {
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
            log.error("Ошибка парсинга questionOrder: {}", e.getMessage());
            return null;
        }
    }

    private String getUserAnswerText(TestSessionAnswerDTO detail) {
        if (!detail.getSelectedAnswers().isEmpty()) {
            return detail.getSelectedAnswers().stream()
                    .map(answer -> answer.getAnswerText())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
        }
        return detail.getAnswerText() != null ? detail.getAnswerText() : "нет ответа";
    }

    private String getCorrectAnswerText(TestSessionAnswerDTO detail) {
        return detail.getCorrectAnswers().stream()
                .map(answer -> answer.getAnswerText())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
