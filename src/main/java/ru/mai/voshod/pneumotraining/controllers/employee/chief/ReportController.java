package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationScenarioRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;
import ru.mai.voshod.pneumotraining.service.employee.chief.ReportService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.LearningPathService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/chief/results")
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final EmployeeRepository employeeRepository;
    private final TestRepository testRepository;
    private final SimulationScenarioRepository simulationScenarioRepository;
    private final LearningPathService learningPathService;

    public ReportController(ReportService reportService,
                            EmployeeRepository employeeRepository,
                            TestRepository testRepository,
                            SimulationScenarioRepository simulationScenarioRepository,
                            LearningPathService learningPathService) {
        this.reportService = reportService;
        this.employeeRepository = employeeRepository;
        this.testRepository = testRepository;
        this.simulationScenarioRepository = simulationScenarioRepository;
        this.learningPathService = learningPathService;
    }

    @GetMapping("/allResults")
    public String allResults(@RequestParam(required = false) Long employeeId,
                             @RequestParam(required = false) Long testId,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
                             Model model) {
        model.addAttribute("sessions", reportService.getAllResults(employeeId, testId, dateFrom, dateTo));
        model.addAttribute("allEmployees", employeeRepository.findAllByOrderByLastNameAsc());
        model.addAttribute("allTests", testRepository.findAllByOrderByIdDesc());
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("selectedTestId", testId);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/chief/results/allResults";
    }

    @GetMapping("/detailsResult/{sessionId}")
    public String detailsResult(@PathVariable Long sessionId, Model model) {
        Optional<TestSessionDTO> sessionOpt = reportService.getSessionResult(sessionId);
        if (sessionOpt.isEmpty()) {
            return "redirect:/employee/chief/results/allResults";
        }

        List<TestSessionAnswerDTO> answerDetails = reportService.getSessionAnswerDetails(sessionId);
        model.addAttribute("sessionDTO", sessionOpt.get());
        model.addAttribute("answerDetails", answerDetails);
        model.addAttribute("recommendations", learningPathService.getRecommendationsById(sessionId));
        return "employee/chief/results/detailsResult";
    }

    @GetMapping("/journal")
    public String journal(Model model) {
        Map<String, Object> journalData = reportService.getJournalData();
        model.addAttribute("journalData", journalData);
        return "employee/chief/results/journal";
    }

    @PostMapping("/exportExamProtocol")
    public ResponseEntity<byte[]> exportExamProtocol(@RequestParam Long testId) {
        byte[] data = reportService.exportExamProtocol(testId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=protocol_" + testId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/exportJournal")
    public ResponseEntity<byte[]> exportJournal() {
        byte[] data = reportService.exportJournal();
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=journal.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // ========== Результаты симуляций ==========

    @GetMapping("/allSimResults")
    public String allSimResults(@RequestParam(required = false) Long employeeId,
                                @RequestParam(required = false) Long scenarioId,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
                                Model model) {
        model.addAttribute("simSessions", reportService.getAllSimulationResults(employeeId, scenarioId, dateFrom, dateTo));
        model.addAttribute("allEmployees", employeeRepository.findAllByOrderByLastNameAsc());
        model.addAttribute("allScenarios", simulationScenarioRepository.findByIsActiveTrueOrderByTitleAsc());
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("selectedScenarioId", scenarioId);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/chief/results/allSimResults";
    }

    @GetMapping("/detailsSimResult/{sessionId}")
    public String detailsSimResult(@PathVariable Long sessionId, Model model) {
        Optional<SimulationSessionDTO> sessionOpt = reportService.getSimulationSessionResult(sessionId);
        if (sessionOpt.isEmpty()) {
            return "redirect:/employee/chief/results/allSimResults";
        }

        SimulationSessionDTO simSession = sessionOpt.get();
        model.addAttribute("simSession", simSession);

        if (simSession.getStepResults() != null && !simSession.getStepResults().isBlank()) {
            try {
                ObjectMapper om = new ObjectMapper();
                List<Map<String, Object>> stepResultsList = om.readValue(
                        simSession.getStepResults(), new TypeReference<>() {});
                model.addAttribute("stepResultsList", stepResultsList);
            } catch (Exception e) {
                model.addAttribute("stepResultsList", List.of());
            }
        } else {
            model.addAttribute("stepResultsList", List.of());
        }
        return "employee/chief/results/detailsSimResult";
    }

    @PostMapping("/exportSimProtocol")
    public ResponseEntity<byte[]> exportSimProtocol(@RequestParam Long scenarioId) {
        byte[] data = reportService.exportSimulationProtocol(scenarioId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=sim_protocol_" + scenarioId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/exportSimResult/{sessionId}")
    public ResponseEntity<byte[]> exportSimResult(@PathVariable Long sessionId) {
        byte[] data = reportService.exportSimulationResultToExcel(sessionId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=sim_result_" + sessionId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/exportSimJournal")
    public ResponseEntity<byte[]> exportSimJournal() {
        byte[] data = reportService.exportSimulationJournal();
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sim_journal.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
