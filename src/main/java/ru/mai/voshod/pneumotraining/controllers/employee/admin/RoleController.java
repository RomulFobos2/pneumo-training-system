package ru.mai.voshod.pneumotraining.controllers.employee.admin;

import com.mai.siarsp.models.Role;
import com.mai.siarsp.service.employee.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/employee/admin/roles")
@Slf4j
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/allRoles")
    public String allRoles(Model model) {
        model.addAttribute("allRoles", roleService.getAllRoles());
        return "employee/admin/roles/allRoles";
    }

    @GetMapping("/addRole")
    public String addRole() {
        return "employee/admin/roles/addRole";
    }

    @PostMapping("/addRole")
    public String addRole(@RequestParam String inputName,
                          @RequestParam String inputDescription,
                          RedirectAttributes redirectAttributes) {
        if (!roleService.createRole(inputName, inputDescription)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при создании роли. Возможно, имя уже занято.");
            return "redirect:/employee/admin/roles/addRole";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Роль успешно создана.");
        return "redirect:/employee/admin/roles/allRoles";
    }

    @GetMapping("/editRole/{id}")
    public String editRole(@PathVariable Long id, Model model) {
        Optional<Role> roleOptional = roleService.getRoleById(id);
        if (roleOptional.isEmpty()) {
            return "redirect:/employee/admin/roles/allRoles";
        }
        Role role = roleOptional.get();
        model.addAttribute("role", role);
        model.addAttribute("isFundamental", roleService.isFundamental(role));
        return "employee/admin/roles/editRole";
    }

    @PostMapping("/editRole/{id}")
    public String editRole(@PathVariable Long id,
                           @RequestParam(required = false) String inputName,
                           @RequestParam String inputDescription,
                           RedirectAttributes redirectAttributes) {
        Optional<Role> roleOptional = roleService.getRoleById(id);
        if (roleOptional.isEmpty()) {
            return "redirect:/employee/admin/roles/allRoles";
        }

        Role role = roleOptional.get();
        boolean success;
        if (roleService.isFundamental(role)) {
            success = roleService.updateRoleDescription(id, inputDescription);
        } else {
            success = roleService.updateRole(id, inputName, inputDescription);
        }

        if (!success) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при сохранении изменений.");
            return "redirect:/employee/admin/roles/editRole/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Роль успешно обновлена.");
        return "redirect:/employee/admin/roles/allRoles";
    }

    @GetMapping("/deleteRole/{id}")
    public String deleteRole(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!roleService.deleteRole(id)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при удалении роли. Убедитесь, что нет сотрудников с этой ролью.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Роль успешно удалена.");
        }
        return "redirect:/employee/admin/roles/allRoles";
    }
}
