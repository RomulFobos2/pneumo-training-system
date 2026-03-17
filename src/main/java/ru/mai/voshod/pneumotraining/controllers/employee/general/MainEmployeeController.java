package ru.mai.voshod.pneumotraining.controllers.employee.general;

import com.mai.siarsp.dto.EmployeeDTO;
import com.mai.siarsp.service.employee.EmployeeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MainEmployeeController {

    private final EmployeeService employeeService;

    public MainEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employee/login")
    public String employeeLogin(Model model) {
        return "employee/general/login";
    }

    @GetMapping("/employee/change-password")
    public String employeeChangePassword(Model model) {
        EmployeeDTO currentEmployeeDTO = employeeService.getAuthenticationEmployeeDTO();
        model.addAttribute("currentEmployee", currentEmployeeDTO);
        return "employee/general/change-password";
    }


    @PostMapping("/employee/change-password")
    public String employeeChangePassword(@RequestParam String passwordNew, @RequestParam String passwordConfirm, Model model) {
        if (passwordNew.equals(passwordConfirm)) {
            if(employeeService.changePassword(employeeService.getBCryptPasswordEncoder().encode(passwordNew))){
                return "redirect:/logout";
            }
            else {
                model.addAttribute("passwordError", "Ошибка при сохранении. Повторите попытку.");
                return "employee/general/change-password";
            }
        }
        else {
            model.addAttribute("passwordError", "Пароли не совпадают. Подтвердите новый пароль верно.");
            return "employee/general/change-password";
        }
    }

    @GetMapping("/employee/profile")
    public String employeeProfile(Model model) {
        EmployeeDTO currentEmployeeDTO = employeeService.getAuthenticationEmployeeDTO();
        if (currentEmployeeDTO != null){
            model.addAttribute("currentEmployee", currentEmployeeDTO);
            String strRoleName = employeeService.getRoleRepository().findByName(currentEmployeeDTO.getRoleName()).orElseThrow().getDescription();
            model.addAttribute("strRoleName", strRoleName);
        }
        return "employee/general/profile";
    }

}
