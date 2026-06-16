package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.enumeration.ProtocolType;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Protocol;
import ru.mai.voshod.pneumotraining.models.SimulationSession;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.repo.ProtocolRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationSessionRepository;
import ru.mai.voshod.pneumotraining.repo.TestSessionRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Генерирует .docx-протоколы проверки знаний по сессиям тестов и симуляций
 * на основе шаблонов из {@code app.protocols.template-dir} (default — docs_tenplates).
 * Сквозная нумерация — через таблицу t_protocol; повторное скачивание возвращает
 * протокол с тем же номером.
 */
@Service
@Slf4j
public class ProtocolService {

    public record ProtocolResult(byte[] data, String filename) {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String TEMPLATE_TEST = "Protokol_Template_test.docx";
    private static final String TEMPLATE_SIM = "Protokol_Template_sim.docx";

    private final TestSessionRepository testSessionRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final ProtocolRepository protocolRepository;

    @Value("${app.protocols.template-dir:docs_tenplates}")
    private String templateDir;

    public ProtocolService(TestSessionRepository testSessionRepository,
                           SimulationSessionRepository simulationSessionRepository,
                           ProtocolRepository protocolRepository) {
        this.testSessionRepository = testSessionRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.protocolRepository = protocolRepository;
    }

    @Transactional
    public Optional<ProtocolResult> generateTestProtocol(Long sessionId) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.error("Протокол теста: сессия id={} не найдена", sessionId);
            return Optional.empty();
        }
        TestSession session = sessionOpt.get();
        Employee employee = session.getEmployee();
        Protocol protocol = resolveOrCreateProtocol(ProtocolType.TEST, sessionId);
        LocalDateTime date = session.getFinishedAt() != null
                ? session.getFinishedAt() : LocalDateTime.now();

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("number", String.valueOf(protocol.getId()));
        vars.put("date", date.format(DATE_FMT));
        vars.put("dateTommorow", date.plusDays(1).format(DATE_FMT));
        vars.put("userFullName", employee.getFullName());
        vars.put("userShortName", buildShortName(employee));
        vars.put("userDepartment", employee.getDepartment() != null
                ? employee.getDepartment().getName() : "");
        vars.put("userPosition", employee.getPosition() != null ? employee.getPosition() : "");
        vars.put("testScorePercent", formatPercent(session.getScorePercent()));

        byte[] data = renderTemplate(TEMPLATE_TEST, vars);
        if (data == null) return Optional.empty();
        return Optional.of(new ProtocolResult(data, "Protokol_test_" + protocol.getId() + ".docx"));
    }

    @Transactional
    public Optional<ProtocolResult> generateSimProtocol(Long sessionId) {
        Optional<SimulationSession> sessionOpt = simulationSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.error("Протокол симуляции: сессия id={} не найдена", sessionId);
            return Optional.empty();
        }
        SimulationSession session = sessionOpt.get();
        Employee employee = session.getEmployee();
        Protocol protocol = resolveOrCreateProtocol(ProtocolType.SIM, sessionId);
        LocalDateTime date = session.getFinishedAt() != null
                ? session.getFinishedAt() : LocalDateTime.now();

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("number", String.valueOf(protocol.getId()));
        vars.put("date", date.format(DATE_FMT));
        vars.put("dateTommorow", date.plusDays(1).format(DATE_FMT));
        vars.put("userFullName", employee.getFullName());
        vars.put("userShortName", buildShortName(employee));
        vars.put("userDepartment", employee.getDepartment() != null
                ? employee.getDepartment().getName() : "");
        vars.put("userPosition", employee.getPosition() != null ? employee.getPosition() : "");
        vars.put("simulationSessionStatus", session.getSessionStatus() != null
                ? session.getSessionStatus().getDisplayName() : "");

        byte[] data = renderTemplate(TEMPLATE_SIM, vars);
        if (data == null) return Optional.empty();
        return Optional.of(new ProtocolResult(data, "Protokol_sim_" + protocol.getId() + ".docx"));
    }

    private Protocol resolveOrCreateProtocol(ProtocolType type, Long sessionId) {
        return protocolRepository.findByTypeAndSessionId(type, sessionId)
                .orElseGet(() -> protocolRepository.save(new Protocol(type, sessionId)));
    }

    private byte[] renderTemplate(String templateFilename, Map<String, String> vars) {
        Path path = Paths.get(templateDir, templateFilename);
        if (!Files.exists(path)) {
            log.error("Шаблон протокола не найден: {}", path.toAbsolutePath());
            return null;
        }
        try (InputStream is = Files.newInputStream(path);
             XWPFDocument doc = new XWPFDocument(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            replaceTokens(doc, vars);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Ошибка генерации протокола из шаблона {}: {}", templateFilename, e.getMessage(), e);
            return null;
        }
    }

    private void replaceTokens(XWPFDocument doc, Map<String, String> vars) {
        // Сортировка по убыванию длины ключа — чтобы dateTommorow заменился раньше date
        List<Map.Entry<String, String>> entries = new ArrayList<>(vars.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (XWPFParagraph p : doc.getParagraphs()) {
            replaceInParagraph(p, entries);
        }
        for (XWPFTable table : doc.getTables()) {
            replaceInTable(table, entries);
        }
        for (XWPFHeader h : doc.getHeaderList()) {
            for (XWPFParagraph p : h.getParagraphs()) replaceInParagraph(p, entries);
            for (XWPFTable t : h.getTables()) replaceInTable(t, entries);
        }
        for (XWPFFooter f : doc.getFooterList()) {
            for (XWPFParagraph p : f.getParagraphs()) replaceInParagraph(p, entries);
            for (XWPFTable t : f.getTables()) replaceInTable(t, entries);
        }
    }

    private void replaceInTable(XWPFTable table, List<Map.Entry<String, String>> entries) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, entries);
                }
                for (XWPFTable nested : cell.getTables()) {
                    replaceInTable(nested, entries);
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, List<Map.Entry<String, String>> entries) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) return;

        StringBuilder fullText = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.getText(0);
            if (t != null) fullText.append(t);
        }
        String original = fullText.toString();
        if (original.isEmpty()) return;

        String replaced = original;
        for (Map.Entry<String, String> e : entries) {
            if (replaced.contains(e.getKey())) {
                replaced = replaced.replace(e.getKey(), e.getValue());
            }
        }
        if (replaced.equals(original)) return;

        XWPFRun first = runs.get(0);
        first.setText(replaced, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    private String formatPercent(Double percent) {
        if (percent == null) return "0,0";
        return String.format(Locale.forLanguageTag("ru"), "%.1f", percent);
    }

    private String buildShortName(Employee employee) {
        StringBuilder sb = new StringBuilder();
        if (employee.getLastName() != null) sb.append(employee.getLastName());
        if (employee.getFirstName() != null && !employee.getFirstName().isBlank()) {
            sb.append(" ").append(employee.getFirstName().charAt(0)).append(".");
        }
        if (employee.getMiddleName() != null && !employee.getMiddleName().isBlank()) {
            sb.append(employee.getMiddleName().charAt(0)).append(".");
        }
        return sb.toString();
    }
}
