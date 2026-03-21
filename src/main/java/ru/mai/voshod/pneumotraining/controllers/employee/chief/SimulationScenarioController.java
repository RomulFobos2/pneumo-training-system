package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.ScenarioStepDTO;
import ru.mai.voshod.pneumotraining.dto.SchemaDataDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.MnemoSchemaService;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationScenarioService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/chief/scenarios")
@Slf4j
public class SimulationScenarioController {

    private final SimulationScenarioService scenarioService;
    private final MnemoSchemaService schemaService;
    private final DepartmentService departmentService;

    public SimulationScenarioController(SimulationScenarioService scenarioService,
                                         MnemoSchemaService schemaService,
                                         DepartmentService departmentService) {
        this.scenarioService = scenarioService;
        this.schemaService = schemaService;
        this.departmentService = departmentService;
    }

    @GetMapping("/allScenarios")
    public String allScenarios(Model model) {
        model.addAttribute("allScenarios", scenarioService.getAllScenarios());
        return "employee/chief/scenarios/allScenarios";
    }

    @GetMapping("/addScenario")
    public String addScenarioForm(Model model) {
        model.addAttribute("allSchemas", schemaService.getAllSchemas());
        model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlat());
        return "employee/chief/scenarios/addScenario";
    }

    @PostMapping("/addScenario")
    public String addScenario(@RequestParam String inputTitle,
                               @RequestParam(required = false) String inputDescription,
                               @RequestParam(required = false) Integer inputTimeLimit,
                               @RequestParam Long inputSchemaId,
                               @RequestParam(required = false) boolean inputIsActive,
                               @RequestParam(required = false) List<Long> inputDepartmentIds,
                               @AuthenticationPrincipal Employee currentUser,
                               RedirectAttributes redirectAttributes) {
        Optional<Long> result = scenarioService.saveScenario(inputTitle, inputDescription,
                inputTimeLimit, inputSchemaId, inputIsActive, inputDepartmentIds, currentUser);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при создании сценария.");
            return "redirect:/employee/chief/scenarios/addScenario";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Сценарий создан. Добавьте шаги.");
        return "redirect:/employee/chief/scenarios/editScenario/" + result.get();
    }

    @GetMapping("/editScenario/{id}")
    public String editScenarioForm(@PathVariable Long id, Model model) {
        Optional<SimulationScenarioDTO> scenarioOpt = scenarioService.getScenarioById(id);
        if (scenarioOpt.isEmpty()) return "redirect:/employee/chief/scenarios/allScenarios";

        model.addAttribute("scenario", scenarioOpt.get());
        model.addAttribute("allSchemas", schemaService.getAllSchemas());
        model.addAttribute("allDepartments", departmentService.getAllDepartmentsFlat());
        return "employee/chief/scenarios/editScenario";
    }

    @PostMapping("/editScenario/{id}")
    public String editScenario(@PathVariable Long id,
                                @RequestParam String inputTitle,
                                @RequestParam(required = false) String inputDescription,
                                @RequestParam(required = false) Integer inputTimeLimit,
                                @RequestParam Long inputSchemaId,
                                @RequestParam(required = false) boolean inputIsActive,
                                @RequestParam(required = false) List<Long> inputDepartmentIds,
                                RedirectAttributes redirectAttributes) {
        Optional<Long> result = scenarioService.editScenario(id, inputTitle, inputDescription,
                inputTimeLimit, inputSchemaId, inputIsActive, inputDepartmentIds);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при обновлении сценария.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Сценарий обновлён.");
        }
        return "redirect:/employee/chief/scenarios/editScenario/" + id;
    }

    @GetMapping("/detailsScenario/{id}")
    public String detailsScenario(@PathVariable Long id, Model model) {
        Optional<SimulationScenarioDTO> scenarioOpt = scenarioService.getScenarioById(id);
        if (scenarioOpt.isEmpty()) return "redirect:/employee/chief/scenarios/allScenarios";
        model.addAttribute("scenario", scenarioOpt.get());
        model.addAttribute("steps", scenarioService.getStepsByScenarioId(id));
        return "employee/chief/scenarios/detailsScenario";
    }

    @GetMapping("/deleteScenario/{id}")
    public String deleteScenario(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (scenarioService.deleteScenario(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Сценарий удалён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении сценария.");
        }
        return "redirect:/employee/chief/scenarios/allScenarios";
    }

    // ========== AJAX ==========

    @GetMapping("/loadSteps/{scenarioId}")
    @ResponseBody
    public ResponseEntity<List<ScenarioStepDTO>> loadSteps(@PathVariable Long scenarioId) {
        return ResponseEntity.ok(scenarioService.getStepsByScenarioId(scenarioId));
    }

    @PostMapping("/saveSteps/{scenarioId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSteps(@PathVariable Long scenarioId,
                                                          @RequestBody List<ScenarioStepDTO> steps) {
        boolean success = scenarioService.saveSteps(scenarioId, steps);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @GetMapping("/loadSchemaElements/{schemaId}")
    @ResponseBody
    public ResponseEntity<SchemaDataDTO> loadSchemaElements(@PathVariable Long schemaId) {
        return ResponseEntity.ok(schemaService.loadSchemaData(schemaId));
    }
}
