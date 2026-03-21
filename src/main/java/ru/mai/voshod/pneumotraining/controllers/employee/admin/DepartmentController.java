package ru.mai.voshod.pneumotraining.controllers.employee.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.DepartmentDTO;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/admin/departments")
@Slf4j
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/check-name")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkName(@RequestParam String name,
                                                           @RequestParam(required = false) Long id) {
        boolean exists;
        if (id != null) {
            exists = departmentService.checkNameExcluding(name, id);
        } else {
            exists = departmentService.checkName(name);
        }
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/allDepartments")
    public String allDepartments(Model model) {
        model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlat());
        return "employee/admin/departments/allDepartments";
    }

    @GetMapping("/addDepartment")
    public String addDepartmentForm(Model model) {
        model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlat());
        return "employee/admin/departments/addDepartment";
    }

    @PostMapping("/addDepartment")
    public String addDepartment(@RequestParam String inputName,
                                @RequestParam(required = false) String inputDescription,
                                @RequestParam(required = false) Long inputParentId,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Optional<Long> result = departmentService.saveDepartment(inputName, inputDescription, inputParentId);
        if (result.isEmpty()) {
            model.addAttribute("errorMessage", "Ошибка при создании. Возможно, название уже занято.");
            model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlat());
            return "employee/admin/departments/addDepartment";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Подразделение создано.");
        return "redirect:/employee/admin/departments/allDepartments";
    }

    @GetMapping("/editDepartment/{id}")
    public String editDepartmentForm(@PathVariable Long id, Model model) {
        Optional<DepartmentDTO> deptOptional = departmentService.getDepartmentById(id);
        if (deptOptional.isEmpty()) {
            return "redirect:/employee/admin/departments/allDepartments";
        }
        model.addAttribute("department", deptOptional.get());
        model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlatExcluding(id));
        return "employee/admin/departments/editDepartment";
    }

    @PostMapping("/editDepartment/{id}")
    public String editDepartment(@PathVariable Long id,
                                 @RequestParam String inputName,
                                 @RequestParam(required = false) String inputDescription,
                                 @RequestParam(required = false) Long inputParentId,
                                 RedirectAttributes redirectAttributes) {
        Optional<Long> result = departmentService.editDepartment(id, inputName, inputDescription, inputParentId);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при сохранении. Возможно, название уже занято.");
            return "redirect:/employee/admin/departments/editDepartment/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Подразделение обновлено.");
        return "redirect:/employee/admin/departments/allDepartments";
    }

    @GetMapping("/deleteDepartment/{id}")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (departmentService.deleteDepartment(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Подразделение удалено.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Нельзя удалить подразделение, в котором есть сотрудники или дочерние подразделения.");
        }
        return "redirect:/employee/admin/departments/allDepartments";
    }
}
