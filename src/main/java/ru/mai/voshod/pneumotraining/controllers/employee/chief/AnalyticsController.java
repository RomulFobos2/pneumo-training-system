package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mai.voshod.pneumotraining.service.employee.chief.AnalyticsService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/employee/chief/analytics")
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long testId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            Model model) {
        model.addAllAttributes(analyticsService.getDashboardData(dateFrom, dateTo, testId, employeeId, departmentId));
        return "employee/chief/analytics/dashboard";
    }

    @PostMapping("/exportDocx")
    public ResponseEntity<byte[]> exportDocx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long testId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId) {

        byte[] docx = analyticsService.exportDashboardDocx(dateFrom, dateTo, testId, employeeId, departmentId);

        String filename = "analytics_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }
}
