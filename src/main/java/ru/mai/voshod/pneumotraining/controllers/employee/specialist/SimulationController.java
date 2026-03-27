package ru.mai.voshod.pneumotraining.controllers.employee.specialist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.chief.MnemoSchemaService;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationAssignmentService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.SimulationService;

import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/specialist/mnemo")
@Slf4j
public class SimulationController {

    private final SimulationService simulationService;
    private final MnemoSchemaService schemaService;
    private final SimulationAssignmentService simulationAssignmentService;

    public SimulationController(SimulationService simulationService,
                                MnemoSchemaService schemaService,
                                SimulationAssignmentService simulationAssignmentService) {
        this.simulationService = simulationService;
        this.schemaService = schemaService;
        this.simulationAssignmentService = simulationAssignmentService;
    }

    @GetMapping("/scenarios")
    public String scenarios(Model model,
                            @AuthenticationPrincipal Employee currentUser,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) java.util.List<String> hideCompleted,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate deadlineFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate deadlineTo,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate createdFrom,
                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate createdTo) {
        boolean resolvedHideCompleted = hideCompleted == null || hideCompleted.contains("true");
        model.addAttribute("scenarios", simulationService.getAvailableScenarios(currentUser));
        model.addAttribute("myAssignments", simulationAssignmentService.getAssignmentsForEmployee(
                currentUser.getId(), q, status, resolvedHideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("hideCompleted", resolvedHideCompleted);
        model.addAttribute("deadlineFrom", deadlineFrom);
        model.addAttribute("deadlineTo", deadlineTo);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        return "employee/specialist/mnemo/scenarios";
    }

    @GetMapping("/start/{scenarioId}")
    public String startForm(@PathVariable Long scenarioId, Model model,
                            @AuthenticationPrincipal Employee currentUser) {
        var scenarios = simulationService.getAvailableScenarios(currentUser);
        var scenarioOpt = scenarios.stream().filter(s -> s.getId().equals(scenarioId)).findFirst();
        if (scenarioOpt.isEmpty()) return "redirect:/employee/specialist/mnemo/scenarios";
        model.addAttribute("scenario", scenarioOpt.get());
        return "employee/specialist/mnemo/startScenario";
    }

    @PostMapping("/start/{scenarioId}")
    public String start(@PathVariable Long scenarioId,
                        @AuthenticationPrincipal Employee currentUser) {
        Optional<Long> sessionId = simulationService.startSimulation(scenarioId, currentUser);
        if (sessionId.isEmpty()) return "redirect:/employee/specialist/mnemo/scenarios";
        return "redirect:/employee/specialist/mnemo/view/" + sessionId.get();
    }

    @GetMapping("/view/{sessionId}")
    public String view(@PathVariable Long sessionId, Model model,
                       @AuthenticationPrincipal Employee currentUser) {
        Optional<SimulationSessionDTO> sessionOpt = simulationService.getSessionState(sessionId, currentUser);
        if (sessionOpt.isEmpty()) {
            // Может быть expired — проверить
            var expiredOpt = simulationService.checkAndExpire(sessionId, currentUser);
            if (expiredOpt.isPresent()) {
                return "redirect:/employee/specialist/mnemo/result/" + sessionId;
            }
            return "redirect:/employee/specialist/mnemo/scenarios";
        }

        SimulationSessionDTO session = sessionOpt.get();
        if (!"IN_PROGRESS".equals(session.getSessionStatus())) {
            return "redirect:/employee/specialist/mnemo/result/" + sessionId;
        }

        model.addAttribute("simSession", session);

        // Загрузить данные схемы для SVG-рендера
        if (session.getSchemaId() != null) {
            model.addAttribute("schemaData", schemaService.loadSchemaData(session.getSchemaId()));
        }

        // Аварийное событие текущего шага (для модалки при загрузке)
        model.addAttribute("currentFaultEvent",
                simulationService.getCurrentStepFaultEvent(sessionId, currentUser));

        return "employee/specialist/mnemo/simulation";
    }

    @GetMapping("/result/{sessionId}")
    public String result(@PathVariable Long sessionId, Model model,
                         @AuthenticationPrincipal Employee currentUser) {
        Optional<SimulationSessionDTO> resultOpt = simulationService.getResult(sessionId, currentUser);
        if (resultOpt.isEmpty()) return "redirect:/employee/specialist/mnemo/scenarios";
        model.addAttribute("simSession", resultOpt.get());
        return "employee/specialist/mnemo/result";
    }

    // ========== AJAX ==========

    @PostMapping("/toggleElement/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleElement(@PathVariable Long sessionId,
                                                              @RequestBody Map<String, String> body,
                                                              @AuthenticationPrincipal Employee currentUser) {
        String elementName = body.get("elementName");
        if (elementName == null) return ResponseEntity.badRequest().build();

        Optional<Map<String, Object>> result = simulationService.toggleElement(sessionId, elementName, currentUser);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/checkStep/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkStep(@PathVariable Long sessionId,
                                                          @AuthenticationPrincipal Employee currentUser) {
        Optional<Map<String, Object>> result = simulationService.checkStep(sessionId, currentUser);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping("/getState/{sessionId}")
    @ResponseBody
    public ResponseEntity<SimulationSessionDTO> getState(@PathVariable Long sessionId,
                                                          @AuthenticationPrincipal Employee currentUser) {
        Optional<SimulationSessionDTO> state = simulationService.getSessionState(sessionId, currentUser);
        return state.map(ResponseEntity::ok).orElse(ResponseEntity.badRequest().build());
    }
}
