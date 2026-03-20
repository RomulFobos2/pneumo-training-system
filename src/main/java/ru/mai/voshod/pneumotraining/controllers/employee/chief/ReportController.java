package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;
import ru.mai.voshod.pneumotraining.service.employee.chief.ReportService;

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

    public ReportController(ReportService reportService,
                            EmployeeRepository employeeRepository,
                            TestRepository testRepository) {
        this.reportService = reportService;
        this.employeeRepository = employeeRepository;
        this.testRepository = testRepository;
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
}
