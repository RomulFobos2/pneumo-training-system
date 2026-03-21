package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.DepartmentDTO;
import ru.mai.voshod.pneumotraining.dto.EmployeeDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.mapper.EmployeeMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestAssignmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/chief/assignments")
@Slf4j
public class TestAssignmentController {

    private final TestAssignmentService testAssignmentService;
    private final TestService testService;
    private final DepartmentService departmentService;
    private final EmployeeRepository employeeRepository;

    public TestAssignmentController(TestAssignmentService testAssignmentService,
                                    TestService testService,
                                    DepartmentService departmentService,
                                    EmployeeRepository employeeRepository) {
        this.testAssignmentService = testAssignmentService;
        this.testService = testService;
        this.departmentService = departmentService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/allAssignments")
    public String allAssignments(Model model) {
        model.addAttribute("allAssignments", testAssignmentService.getAllAssignments());
        return "employee/chief/assignments/allAssignments";
    }

    @GetMapping("/addAssignment")
    public String addAssignmentForm(Model model) {
        model.addAttribute("allDepartments", departmentService.getAllDepartments());
        model.addAttribute("allEmployees", getSpecialistOperatorEmployees());
        return "employee/chief/assignments/addAssignment";
    }

    @PostMapping("/addAssignment")
    public String addAssignment(@RequestParam Long inputTestId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inputDeadline,
                                @RequestParam(name = "inputEmployeeIds") List<Long> inputEmployeeIds,
                                @AuthenticationPrincipal Employee currentUser,
                                RedirectAttributes redirectAttributes) {
        Optional<Long> result = testAssignmentService.createAssignment(inputTestId, inputDeadline, inputEmployeeIds, currentUser);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при создании назначения.");
            return "redirect:/employee/chief/assignments/addAssignment";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Назначение создано.");
        return "redirect:/employee/chief/assignments/allAssignments";
    }

    @GetMapping("/detailsAssignment/{id}")
    public String detailsAssignment(@PathVariable Long id, Model model) {
        Optional<TestAssignmentDTO> assignmentOptional = testAssignmentService.getAssignmentById(id);
        if (assignmentOptional.isEmpty()) {
            return "redirect:/employee/chief/assignments/allAssignments";
        }
        model.addAttribute("assignment", assignmentOptional.get());
        model.addAttribute("assignmentEmployees", testAssignmentService.getAssignmentEmployees(id));
        return "employee/chief/assignments/detailsAssignment";
    }

    @GetMapping("/deleteAssignment/{id}")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (testAssignmentService.deleteAssignment(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Назначение удалено.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении назначения.");
        }
        return "redirect:/employee/chief/assignments/allAssignments";
    }

    @GetMapping("/testsByDepartment/{departmentId}")
    @ResponseBody
    public ResponseEntity<List<TestDTO>> testsByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(testService.getTestsForDepartment(departmentId));
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

    @GetMapping("/testsByEmployee/{employeeId}")
    @ResponseBody
    public ResponseEntity<List<TestDTO>> testsByEmployee(@PathVariable Long employeeId) {
        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        if (empOpt.isEmpty() || empOpt.get().getDepartment() == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(testService.getTestsForDepartment(empOpt.get().getDepartment().getId()));
    }

    private List<EmployeeDTO> getSpecialistOperatorEmployees() {
        List<Employee> specialists = employeeRepository.findAllByRoleName("ROLE_EMPLOYEE_SPECIALIST");
        List<Employee> operators = employeeRepository.findAllByRoleName("ROLE_EMPLOYEE_OPERATOR");
        List<Employee> all = new java.util.ArrayList<>(specialists);
        all.addAll(operators);
        return EmployeeMapper.INSTANCE.toDTOList(
                all.stream().filter(Employee::isActive).sorted((a, b) -> a.getLastName().compareTo(b.getLastName())).collect(Collectors.toList()));
    }
}
