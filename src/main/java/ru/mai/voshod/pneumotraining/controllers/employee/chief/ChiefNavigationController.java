package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/employee/chief/navigation")
public class ChiefNavigationController {

    @GetMapping("/assignments")
    public String assignmentsHub() {
        return "employee/chief/navigation/assignmentsHub";
    }

    @GetMapping("/mnemo")
    public String mnemoHub() {
        return "employee/chief/navigation/mnemoHub";
    }

    @GetMapping("/results")
    public String resultsHub() {
        return "employee/chief/navigation/resultsHub";
    }
}
