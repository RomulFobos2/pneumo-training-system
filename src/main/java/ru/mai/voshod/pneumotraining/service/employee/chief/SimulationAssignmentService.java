package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.AssignedScenarioDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationAssignmentMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationScenarioRepository;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SimulationAssignmentService {

    private final SimulationAssignmentRepository assignmentRepository;
    private final SimulationAssignmentEmployeeRepository assignmentEmployeeRepository;
    private final SimulationScenarioRepository scenarioRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public SimulationAssignmentService(SimulationAssignmentRepository assignmentRepository,
                                       SimulationAssignmentEmployeeRepository assignmentEmployeeRepository,
                                       SimulationScenarioRepository scenarioRepository,
                                       EmployeeRepository employeeRepository,
                                       NotificationService notificationService) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentEmployeeRepository = assignmentEmployeeRepository;
        this.scenarioRepository = scenarioRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentDTO> getAllAssignments() {
        List<SimulationAssignment> assignments = assignmentRepository.findAllByOrderByCreatedAtDesc();
        List<SimulationAssignmentDTO> dtos = new ArrayList<>();
        for (SimulationAssignment a : assignments) {
            SimulationAssignmentDTO dto = SimulationAssignmentMapper.INSTANCE.toDTO(a);
            List<SimulationAssignmentEmployee> employees = a.getAssignedEmployees();
            dto.setTotalAssigned(employees.size());
            dto.setCompletedCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
            dto.setOverdueCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Optional<SimulationAssignmentDTO> getAssignmentById(Long id) {
        return assignmentRepository.findById(id).map(a -> {
            SimulationAssignmentDTO dto = SimulationAssignmentMapper.INSTANCE.toDTO(a);
            List<SimulationAssignmentEmployee> employees = a.getAssignedEmployees();
            dto.setTotalAssigned(employees.size());
            dto.setCompletedCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
            dto.setOverdueCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentEmployeeDTO> getAssignmentEmployees(Long assignmentId) {
        List<SimulationAssignmentEmployee> employees = assignmentEmployeeRepository.findByAssignmentId(assignmentId);
        return SimulationAssignmentMapper.INSTANCE.toEmployeeDTOList(employees);
    }

    @Transactional
    public Optional<Long> createAssignment(Long scenarioId, LocalDate deadline, List<Long> employeeIds, Employee createdBy) {
        log.info("Создание назначения сценария: scenarioId={}, deadline={}, employees={}", scenarioId, deadline, employeeIds.size());

        Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(scenarioId);
        if (scenarioOpt.isEmpty()) {
            log.error("Сценарий не найден: id={}", scenarioId);
            return Optional.empty();
        }

        SimulationScenario scenario = scenarioOpt.get();
        SimulationAssignment assignment = new SimulationAssignment(scenario, deadline, createdBy);
        assignmentRepository.save(assignment);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String scenarioLink = "/employee/specialist/mnemo/scenarios";

        for (Long empId : employeeIds) {
            employeeRepository.findById(empId).ifPresent(employee -> {
                SimulationAssignmentEmployee ae = new SimulationAssignmentEmployee(assignment, employee);
                assignmentEmployeeRepository.save(ae);

                notificationService.createNotification(employee,
                        "Вам назначен сценарий мнемосхемы «" + scenario.getTitle() + "». Срок сдачи: " + deadline.format(fmt),
                        scenarioLink);
            });
        }

        log.info("Назначение сценария создано: id={}, scenarioId={}, employees={}", assignment.getId(), scenarioId, employeeIds.size());
        return Optional.of(assignment.getId());
    }

    @Transactional
    public boolean deleteAssignment(Long id) {
        if (!assignmentRepository.existsById(id)) {
            return false;
        }
        assignmentRepository.deleteById(id);
        log.info("Назначение сценария удалено: id={}", id);
        return true;
    }

    @Transactional(readOnly = true)
    public List<AssignedScenarioDTO> getAssignedScenariosForEmployee(Long employeeId) {
        List<SimulationAssignmentEmployee> pending = assignmentEmployeeRepository
                .findByEmployeeIdAndStatus(employeeId, AssignmentStatus.PENDING);

        List<AssignedScenarioDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (SimulationAssignmentEmployee ae : pending) {
            SimulationScenario scenario = ae.getAssignment().getScenario();

            AssignedScenarioDTO dto = new AssignedScenarioDTO();
            dto.setAssignmentEmployeeId(ae.getId());
            dto.setScenarioId(scenario.getId());
            dto.setScenarioTitle(scenario.getTitle());
            dto.setScenarioDescription(scenario.getDescription());
            dto.setDeadline(ae.getAssignment().getDeadline());
            dto.setStatusName(ae.getStatus().name());
            dto.setStatusDisplayName(ae.getStatus().getDisplayName());
            dto.setDaysUntilDeadline(ChronoUnit.DAYS.between(today, ae.getAssignment().getDeadline()));
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AssignedScenarioDTO> getFailedScenariosForEmployee(Long employeeId) {
        List<SimulationAssignmentEmployee> failed = assignmentEmployeeRepository
                .findByEmployeeIdAndStatus(employeeId, AssignmentStatus.FAILED);

        List<AssignedScenarioDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (SimulationAssignmentEmployee ae : failed) {
            SimulationScenario scenario = ae.getAssignment().getScenario();

            AssignedScenarioDTO dto = new AssignedScenarioDTO();
            dto.setAssignmentEmployeeId(ae.getId());
            dto.setScenarioId(scenario.getId());
            dto.setScenarioTitle(scenario.getTitle());
            dto.setScenarioDescription(scenario.getDescription());
            dto.setDeadline(ae.getAssignment().getDeadline());
            dto.setStatusName(ae.getStatus().name());
            dto.setStatusDisplayName(ae.getStatus().getDisplayName());
            dto.setDaysUntilDeadline(ChronoUnit.DAYS.between(today, ae.getAssignment().getDeadline()));
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public void markAssignmentCompleted(Long employeeId, Long scenarioId, SimulationSession session) {
        List<SimulationAssignmentEmployee> pending = assignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_ScenarioIdAndStatus(employeeId, scenarioId, AssignmentStatus.PENDING);

        for (SimulationAssignmentEmployee ae : pending) {
            ae.setStatus(AssignmentStatus.COMPLETED);
            ae.setCompletedSimulationSession(session);
            assignmentEmployeeRepository.save(ae);
            log.info("Назначение сценария выполнено: assignmentEmployeeId={}, scenarioId={}, employeeId={}",
                    ae.getId(), scenarioId, employeeId);
        }
    }

    @Transactional
    public void markAssignmentFailed(Long employeeId, Long scenarioId, SimulationSession session) {
        List<SimulationAssignmentEmployee> pending = assignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_ScenarioIdAndStatus(employeeId, scenarioId, AssignmentStatus.PENDING);

        for (SimulationAssignmentEmployee ae : pending) {
            ae.setStatus(AssignmentStatus.FAILED);
            ae.setCompletedSimulationSession(session);
            assignmentEmployeeRepository.save(ae);
            log.info("Назначение сценария не сдано: assignmentEmployeeId={}, scenarioId={}, employeeId={}",
                    ae.getId(), scenarioId, employeeId);
        }
    }
}
