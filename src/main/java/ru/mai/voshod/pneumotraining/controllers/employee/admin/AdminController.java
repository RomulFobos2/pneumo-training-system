package ru.mai.voshod.pneumotraining.controllers.employee.admin;

import com.mai.siarsp.dto.EmployeeDTO;
import com.mai.siarsp.service.employee.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Slf4j
public class AdminController {

    private final EmployeeService employeeService;

    public AdminController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employee/admin/employees/editAdmin/{id}")
    public String editAdminProfile(@PathVariable long id, Model model) {
        EmployeeDTO employeeDTO = employeeService.getAuthenticationEmployeeDTO();
        if (employeeDTO == null || employeeDTO.getId() != id) {
            return "redirect:employee/employee-profile";
        }
        model.addAttribute("employeeDTO", employeeDTO);
        return "employee/admin/editAdmin";
    }

    @PostMapping("/employee/admin/employees/editAdmin/{id}")
    public String editAdminProfile(@PathVariable(value = "id") long id,
                                   @RequestParam String inputLastName, @RequestParam String inputFirstName,
                                   @RequestParam String inputPatronymicName, @RequestParam String inputUsername,
                                   Model model, RedirectAttributes redirectAttributes) {
        if (!employeeService.editEmployee(id, inputLastName, inputFirstName, inputPatronymicName, inputUsername, "ROLE_EMPLOYEE_ADMIN")) {
            redirectAttributes.addFlashAttribute("usernameError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/admin/editAdmin/" + id;
        } else {
            return "redirect:/logout";
        }
    }

}
