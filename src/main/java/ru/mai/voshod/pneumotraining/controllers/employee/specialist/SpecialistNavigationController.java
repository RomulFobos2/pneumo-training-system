package ru.mai.voshod.pneumotraining.controllers.employee.specialist;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/employee/specialist/navigation")
public class SpecialistNavigationController {

    @GetMapping("/practice")
    public String practiceHub() {
        return "employee/specialist/navigation/practiceHub";
    }
}
