package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.MnemoSchemaDTO;
import ru.mai.voshod.pneumotraining.dto.SchemaDataDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.chief.MnemoSchemaService;

import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/chief/schemas")
@Slf4j
public class MnemoSchemaController {

    private final MnemoSchemaService mnemoSchemaService;

    public MnemoSchemaController(MnemoSchemaService mnemoSchemaService) {
        this.mnemoSchemaService = mnemoSchemaService;
    }

    @GetMapping("/allSchemas")
    public String allSchemas(Model model) {
        model.addAttribute("allSchemas", mnemoSchemaService.getAllSchemas());
        return "employee/chief/schemas/allSchemas";
    }

    @GetMapping("/addSchema")
    public String addSchemaForm() {
        return "employee/chief/schemas/addSchema";
    }

    @PostMapping("/addSchema")
    public String addSchema(@RequestParam String inputTitle,
                            @RequestParam(required = false) String inputDescription,
                            @RequestParam(required = false) Integer inputWidth,
                            @RequestParam(required = false) Integer inputHeight,
                            @AuthenticationPrincipal Employee currentUser,
                            RedirectAttributes redirectAttributes) {
        Optional<Long> result = mnemoSchemaService.saveSchema(inputTitle, inputDescription,
                inputWidth, inputHeight, currentUser);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при создании схемы. Возможно, схема с таким названием уже существует.");
            return "redirect:/employee/chief/schemas/addSchema";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Схема создана. Откройте редактор для добавления элементов.");
        return "redirect:/employee/chief/schemas/editSchema/" + result.get();
    }

    @GetMapping("/editSchema/{id}")
    public String editSchema(@PathVariable Long id, Model model) {
        Optional<MnemoSchemaDTO> schemaOpt = mnemoSchemaService.getSchemaById(id);
        if (schemaOpt.isEmpty()) {
            return "redirect:/employee/chief/schemas/allSchemas";
        }
        model.addAttribute("schema", schemaOpt.get());
        return "employee/chief/schemas/editSchema";
    }

    @PostMapping("/editSchema/{id}")
    public String editSchemaPost(@PathVariable Long id,
                                 @RequestParam String inputTitle,
                                 @RequestParam(required = false) String inputDescription,
                                 @RequestParam(required = false) Integer inputWidth,
                                 @RequestParam(required = false) Integer inputHeight,
                                 RedirectAttributes redirectAttributes) {
        Optional<Long> result = mnemoSchemaService.editSchema(id, inputTitle, inputDescription, inputWidth, inputHeight);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при обновлении схемы.");
            return "redirect:/employee/chief/schemas/editSchema/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Настройки схемы обновлены.");
        return "redirect:/employee/chief/schemas/editSchema/" + id;
    }

    @GetMapping("/detailsSchema/{id}")
    public String detailsSchema(@PathVariable Long id, Model model) {
        Optional<MnemoSchemaDTO> schemaOpt = mnemoSchemaService.getSchemaById(id);
        if (schemaOpt.isEmpty()) {
            return "redirect:/employee/chief/schemas/allSchemas";
        }
        model.addAttribute("schema", schemaOpt.get());
        return "employee/chief/schemas/detailsSchema";
    }

    @GetMapping("/deleteSchema/{id}")
    public String deleteSchema(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String error = mnemoSchemaService.deleteSchema(id);
        if (error == null) {
            redirectAttributes.addFlashAttribute("successMessage", "Схема удалена.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", error);
        }
        return "redirect:/employee/chief/schemas/allSchemas";
    }

    // ========== AJAX ==========

    @GetMapping("/loadSchemaData/{id}")
    @ResponseBody
    public ResponseEntity<SchemaDataDTO> loadSchemaData(@PathVariable Long id) {
        return ResponseEntity.ok(mnemoSchemaService.loadSchemaData(id));
    }

    @PostMapping("/saveSchemaData/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSchemaData(@PathVariable Long id,
                                                               @RequestBody SchemaDataDTO data) {
        boolean success = mnemoSchemaService.saveSchemaData(id, data);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @GetMapping("/check-title")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkTitle(@RequestParam String title,
                                                            @RequestParam(required = false) Long id) {
        boolean exists = mnemoSchemaService.checkTitle(title, id);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
