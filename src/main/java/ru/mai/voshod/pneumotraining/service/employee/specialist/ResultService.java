package ru.mai.voshod.pneumotraining.service.employee.specialist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.TestSessionMapper;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.SimulationSession;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.repo.SimulationSessionRepository;
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
    private final SimulationSessionRepository simulationSessionRepository;
    private final TestingService testingService;
    private final ObjectMapper objectMapper;

    public ResultService(TestSessionRepository testSessionRepository,
                         TestSessionAnswerRepository testSessionAnswerRepository,
                         SimulationSessionRepository simulationSessionRepository,
                         TestingService testingService) {
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.testingService = testingService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<TestSessionDTO> getMyResults(Employee employee) {
        List<TestSession> sessions = testSessionRepository.findByEmployeeIdOrderByStartedAtDesc(employee.getId());
        return sessions.stream()
                .filter(s -> s.getSessionStatus() != TestSessionStatus.IN_PROGRESS)
                .map(this::toDTO)
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
        if (sessionOpt.isEmpty()) return null;

        TestSessionDTO session = sessionOpt.get();
        List<TestSessionAnswerDTO> details = getSessionAnswerDetails(sessionId, employee);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Результат тестирования");

            // Стили
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

            // Шапка
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("Отчёт самотестирования");
            c0.setCellStyle(boldStyle);

            sheet.createRow(2).createCell(0).setCellValue("Сотрудник:");
            sheet.getRow(2).createCell(1).setCellValue(session.getEmployeeFullName());

            sheet.createRow(3).createCell(0).setCellValue("Тест:");
            sheet.getRow(3).createCell(1).setCellValue(session.getTestTitle());

            sheet.createRow(4).createCell(0).setCellValue("Дата:");
            sheet.getRow(4).createCell(1).setCellValue(session.getStartedAt().format(dtf));

            sheet.createRow(5).createCell(0).setCellValue("Результат:");
            sheet.getRow(5).createCell(1).setCellValue(
                    session.getScore() + " / " + session.getTotalScore()
                            + " (" + String.format("%.1f", session.getScorePercent()) + "%)");

            sheet.createRow(6).createCell(0).setCellValue("Статус:");
            sheet.getRow(6).createCell(1).setCellValue(
                    Boolean.TRUE.equals(session.getIsPassed()) ? "Пройден" : "Не пройден");

            // Таблица ответов
            Row headerRow = sheet.createRow(8);
            String[] headers = {"#", "Вопрос", "Тип", "Ваш ответ", "Правильный ответ", "Результат"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < details.size(); i++) {
                TestSessionAnswerDTO d = details.get(i);
                Row row = sheet.createRow(9 + i);
                row.createCell(0).setCellValue(i + 1);

                Cell questionCell = row.createCell(1);
                questionCell.setCellValue(d.getQuestionText());
                questionCell.setCellStyle(wrapStyle);

                row.createCell(2).setCellValue(d.getQuestionTypeName());

                // Ваш ответ
                String userAnswer = getUserAnswerText(d);
                Cell userCell = row.createCell(3);
                userCell.setCellValue(userAnswer);
                userCell.setCellStyle(wrapStyle);

                // Правильный ответ
                String correctAnswer = d.getCorrectAnswers().stream()
                        .map(a -> a.getAnswerText())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                Cell correctCell = row.createCell(4);
                correctCell.setCellValue(correctAnswer);
                correctCell.setCellStyle(wrapStyle);

                row.createCell(5).setCellValue(d.isCorrect() ? "Верно" : "Неверно");
            }

            // Авторазмер колонок
            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 10000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 8000);
            sheet.setColumnWidth(4, 8000);
            sheet.setColumnWidth(5, 3000);

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
        if (sessionOpt.isEmpty()) return null;

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

            // Таблица шагов
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
            log.error("Ошибка парсинга questionOrder: {}", e.getMessage());
            return null;
        }
    }

    private String getUserAnswerText(TestSessionAnswerDTO d) {
        if (!d.getSelectedAnswers().isEmpty()) {
            return d.getSelectedAnswers().stream()
                    .map(a -> a.getAnswerText())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }
        return d.getAnswerText() != null ? d.getAnswerText() : "нет ответа";
    }
}
