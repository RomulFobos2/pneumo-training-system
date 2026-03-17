package ru.mai.voshod.pneumotraining.controllers.employee.general;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmployeePanelController {

    @GetMapping("/employee/admin/administrationPanel")
    public String administrationPanel(Model model) {
        return "employee/admin/administrationPanel";
    }

}
