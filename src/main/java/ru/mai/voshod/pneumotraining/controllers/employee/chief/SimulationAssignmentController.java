package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.*;
import ru.mai.voshod.pneumotraining.mapper.EmployeeMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationAssignmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationScenarioService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/chief/simAssignments")
@Slf4j
public class SimulationAssignmentController {

    private final SimulationAssignmentService simulationAssignmentService;
    private final SimulationScenarioService scenarioService;
    private final DepartmentService departmentService;
    private final EmployeeRepository employeeRepository;

    public SimulationAssignmentController(SimulationAssignmentService simulationAssignmentService,
                                          SimulationScenarioService scenarioService,
                                          DepartmentService departmentService,
                                          EmployeeRepository employeeRepository) {
        this.simulationAssignmentService = simulationAssignmentService;
        this.scenarioService = scenarioService;
        this.departmentService = departmentService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/allAssignments")
    public String allAssignments(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) List<String> hideCompleted,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                 Model model) {
        boolean resolvedHideCompleted = hideCompleted == null || hideCompleted.contains("true");
        model.addAttribute("allAssignments", simulationAssignmentService.getAllAssignments(
                q, status, resolvedHideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("hideCompleted", resolvedHideCompleted);
        model.addAttribute("deadlineFrom", deadlineFrom);
        model.addAttribute("deadlineTo", deadlineTo);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        return "employee/chief/simAssignments/allAssignments";
    }

    @GetMapping("/addAssignment")
    public String addAssignmentForm(Model model) {
        model.addAttribute("allDepartments", departmentService.getAllDepartments());
        model.addAttribute("allEmployees", getSpecialistOperatorEmployees());
        return "employee/chief/simAssignments/addAssignment";
    }

    @PostMapping("/addAssignment")
    public String addAssignment(@RequestParam Long inputScenarioId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inputDeadline,
                                @RequestParam(name = "inputEmployeeIds") List<Long> inputEmployeeIds,
                                @AuthenticationPrincipal Employee currentUser,
                                RedirectAttributes redirectAttributes) {
        Optional<Long> result = simulationAssignmentService.createAssignment(inputScenarioId, inputDeadline, inputEmployeeIds, currentUser);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при создании назначения.");
            return "redirect:/employee/chief/simAssignments/addAssignment";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Назначение создано.");
        return "redirect:/employee/chief/simAssignments/allAssignments";
    }

    @GetMapping("/detailsAssignment/{id}")
    public String detailsAssignment(@PathVariable Long id,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) List<String> hideCompleted,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                    Model model) {
        boolean resolvedHideCompleted = hideCompleted == null || hideCompleted.contains("true");
        Optional<SimulationAssignmentDTO> assignmentOptional = simulationAssignmentService.getAssignmentById(id);
        if (assignmentOptional.isEmpty()) {
            return "redirect:/employee/chief/simAssignments/allAssignments";
        }
        model.addAttribute("assignment", assignmentOptional.get());
        model.addAttribute("assignmentEmployees", simulationAssignmentService.getAssignmentEmployees(
                id, q, status, resolvedHideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("hideCompleted", resolvedHideCompleted);
        model.addAttribute("deadlineFrom", deadlineFrom);
        model.addAttribute("deadlineTo", deadlineTo);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        return "employee/chief/simAssignments/detailsAssignment";
    }

    @GetMapping("/deleteAssignment/{id}")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (simulationAssignmentService.deleteAssignment(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Назначение удалено.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении назначения.");
        }
        return "redirect:/employee/chief/simAssignments/allAssignments";
    }

    @GetMapping("/scenariosByDepartment/{departmentId}")
    @ResponseBody
    public ResponseEntity<List<SimulationScenarioDTO>> scenariosByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(scenarioService.getScenariosForDepartment(departmentId));
    }

    @GetMapping("/scenariosByEmployee/{employeeId}")
    @ResponseBody
    public ResponseEntity<List<SimulationScenarioDTO>> scenariosByEmployee(@PathVariable Long employeeId) {
        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        if (empOpt.isEmpty() || empOpt.get().getDepartment() == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(scenarioService.getScenariosForDepartment(empOpt.get().getDepartment().getId()));
    }

    @GetMapping("/employeesByDepartment/{departmentId}")
    @ResponseBody
    public ResponseEntity<List<EmployeeDTO>> employeesByDepartment(@PathVariable Long departmentId) {
        List<Long> deptIds = departmentService.getDescendantIdsIncludingSelf(departmentId);
        List<Employee> employees = employeeRepository.findAllByOrderByLastNameAsc().stream()
                .filter(e -> e.isActive()
                        && e.getDepartment() != null
                        && deptIds.contains(e.getDepartment().getId())
                        && (e.getRole().getName().equals("ROLE_EMPLOYEE_SPECIALIST")
                        || e.getRole().getName().equals("ROLE_EMPLOYEE_OPERATOR")))
                .toList();
        return ResponseEntity.ok(EmployeeMapper.INSTANCE.toDTOList(employees));
    }

    private List<EmployeeDTO> getSpecialistOperatorEmployees() {
        List<Employee> specialists = employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_SPECIALIST");
        List<Employee> operators = employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_OPERATOR");
        List<Employee> all = new java.util.ArrayList<>(specialists);
        all.addAll(operators);
        return EmployeeMapper.INSTANCE.toDTOList(
                all.stream().filter(Employee::isActive).sorted((a, b) -> a.getLastName().compareTo(b.getLastName())).collect(Collectors.toList()));
    }
}
