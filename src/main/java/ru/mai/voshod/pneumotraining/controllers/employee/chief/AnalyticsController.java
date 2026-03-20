package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.mai.voshod.pneumotraining.service.employee.chief.AnalyticsService;

@Controller
@RequestMapping("/employee/chief/analytics")
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAllAttributes(analyticsService.getDashboardData());
        return "employee/chief/analytics/dashboard";
    }
}
