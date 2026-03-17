package ru.mai.voshod.pneumotraining.controllers.general;

import com.mai.siarsp.dto.EmployeeDTO;
import com.mai.siarsp.models.Employee;
import com.mai.siarsp.service.employee.EmployeeService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    private final EmployeeService employeeService;

    public MainController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/")
    public String home(Model model) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Employee) {
            EmployeeDTO currentEmployeeDTO = employeeService.getAuthenticationEmployeeDTO();
            if(currentEmployeeDTO != null && currentEmployeeDTO.isNeedChangePass()){
                return "redirect:/employee/change-password";
            }
        }
        return "general/home";
    }
}
