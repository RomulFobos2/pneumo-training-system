package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.AssignedScenarioDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.enumeration.ScenarioType;
import ru.mai.voshod.pneumotraining.mapper.SimulationAssignmentMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.SimulationAssignment;
import ru.mai.voshod.pneumotraining.models.SimulationAssignmentEmployee;
import ru.mai.voshod.pneumotraining.models.SimulationScenario;
import ru.mai.voshod.pneumotraining.models.SimulationSession;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationScenarioRepository;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class SimulationAssignmentService {

    private final SimulationAssignmentRepository assignmentRepository;
    private final SimulationAssignmentEmployeeRepository assignmentEmployeeRepository;
    private final SimulationScenarioRepository scenarioRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final SimulationScenarioService simulationScenarioService;

    public SimulationAssignmentService(SimulationAssignmentRepository assignmentRepository,
                                       SimulationAssignmentEmployeeRepository assignmentEmployeeRepository,
                                       SimulationScenarioRepository scenarioRepository,
                                       EmployeeRepository employeeRepository,
                                       NotificationService notificationService,
                                       SimulationScenarioService simulationScenarioService) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentEmployeeRepository = assignmentEmployeeRepository;
        this.scenarioRepository = scenarioRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.simulationScenarioService = simulationScenarioService;
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentDTO> getAllAssignments(String q,
                                                           String status,
                                                           Boolean hideCompleted,
                                                           LocalDate deadlineFrom,
                                                           LocalDate deadlineTo,
                                                           LocalDate createdFrom,
                                                           LocalDate createdTo) {
        return assignmentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAssignmentDTO)
                .filter(dto -> matchesAssignmentFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationAssignmentDTO> getAssignmentById(Long id) {
        return assignmentRepository.findById(id).map(this::toAssignmentDTO);
    }

    @Transactional(readOnly = true)
    public List<SimulationAssignmentEmployeeDTO> getAssignmentEmployees(Long assignmentId,
                                                                        String q,
                                                                        String status,
                                                                        Boolean hideCompleted,
                                                                        LocalDate deadlineFrom,
                                                                        LocalDate deadlineTo,
                                                                        LocalDate createdFrom,
                                                                        LocalDate createdTo) {
        return assignmentEmployeeRepository.findByAssignmentId(assignmentId)
                .stream()
                .map(SimulationAssignmentMapper.INSTANCE::toEmployeeDTO)
                .filter(dto -> matchesEmployeeFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional
    public Optional<Long> createAssignment(Long scenarioId, LocalDate deadline, List<Long> employeeIds, Employee createdBy) {
        int employeeCount = employeeIds != null ? employeeIds.size() : 0;
        log.info("Создание назначения сценария: scenarioId={}, deadline={}, employees={}", scenarioId, deadline, employeeCount);

        if (employeeIds == null || employeeIds.isEmpty()) {
            log.warn("Не переданы сотрудники для назначения сценария: scenarioId={}", scenarioId);
            return Optional.empty();
        }

        Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(scenarioId);
        if (scenarioOpt.isEmpty()) {
            log.error("Сценарий не найден: id={}", scenarioId);
            return Optional.empty();
        }

        SimulationScenario scenario = scenarioOpt.get();
        if (scenario.getScenarioType() != ScenarioType.NORMAL) {
            log.warn("Нельзя назначить аварийный сценарий: id={}", scenarioId);
            return Optional.empty();
        }
        if (simulationScenarioService.isAvailableWithoutAssignment(scenario)) {
            log.warn("Нельзя назначить сценарий со свободным доступом: id={}", scenarioId);
            return Optional.empty();
        }

        List<Employee> employees = employeeRepository.findAllById(employeeIds);
        if (employees.size() != employeeIds.size()) {
            log.warn("Часть сотрудников для назначения не найдена: requested={}, found={}", employeeIds.size(), employees.size());
            return Optional.empty();
        }

        boolean hasEmployeeOutsideAllowedDepartments = employees.stream().anyMatch(employee ->
                employee.getDepartment() == null
                        || !simulationScenarioService.isDepartmentAllowedForScenario(scenario, employee.getDepartment().getId()));
        if (hasEmployeeOutsideAllowedDepartments) {
            log.warn("Есть сотрудники вне разрешённых подразделений для сценария: scenarioId={}", scenarioId);
            return Optional.empty();
        }

        SimulationAssignment assignment = new SimulationAssignment(scenario, deadline, createdBy);
        assignmentRepository.save(assignment);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String scenarioLink = "/employee/specialist/mnemo/scenarios";

        for (Employee employee : employees) {
            SimulationAssignmentEmployee ae = new SimulationAssignmentEmployee(assignment, employee);
            assignmentEmployeeRepository.save(ae);

            notificationService.createNotification(employee,
                    "Вам назначен сценарий мнемосхемы «" + scenario.getTitle() + "». Срок сдачи: " + deadline.format(fmt),
                    scenarioLink);
        }

        log.info("Назначение сценария создано: id={}, scenarioId={}, employees={}", assignment.getId(), scenarioId, employees.size());
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
    public List<AssignedScenarioDTO> getAssignmentsForEmployee(Long employeeId,
                                                               String q,
                                                               String status,
                                                               Boolean hideCompleted,
                                                               LocalDate deadlineFrom,
                                                               LocalDate deadlineTo,
                                                               LocalDate createdFrom,
                                                               LocalDate createdTo) {
        return assignmentEmployeeRepository.findByEmployeeIdOrderByAssignment_CreatedAtDesc(employeeId)
                .stream()
                .map(this::toAssignedScenarioDTO)
                .filter(dto -> matchesAssignedScenarioFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasPendingAssignment(Long employeeId, Long scenarioId) {
        return !assignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_ScenarioIdAndStatus(employeeId, scenarioId, AssignmentStatus.PENDING)
                .isEmpty();
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

    private SimulationAssignmentDTO toAssignmentDTO(SimulationAssignment assignment) {
        SimulationAssignmentDTO dto = SimulationAssignmentMapper.INSTANCE.toDTO(assignment);
        List<SimulationAssignmentEmployee> employees = assignment.getAssignedEmployees();
        int completedCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED)
                .count();
        int overdueCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE)
                .count();
        int failedCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.FAILED)
                .count();

        dto.setTotalAssigned(employees.size());
        dto.setCompletedCount(completedCount);
        dto.setOverdueCount(overdueCount);
        dto.setFailedCount(failedCount);
        dto.setFullyCompleted(!employees.isEmpty() && completedCount == employees.size());
        return dto;
    }

    private AssignedScenarioDTO toAssignedScenarioDTO(SimulationAssignmentEmployee ae) {
        SimulationScenario scenario = ae.getAssignment().getScenario();
        LocalDate today = LocalDate.now();

        AssignedScenarioDTO dto = new AssignedScenarioDTO();
        dto.setAssignmentEmployeeId(ae.getId());
        dto.setScenarioId(scenario.getId());
        dto.setScenarioTitle(scenario.getTitle());
        dto.setScenarioDescription(scenario.getDescription());
        dto.setDeadline(ae.getAssignment().getDeadline());
        dto.setCreatedAt(ae.getAssignment().getCreatedAt());
        dto.setStatusName(ae.getStatus().name());
        dto.setStatusDisplayName(ae.getStatus().getDisplayName());
        dto.setDaysUntilDeadline(ChronoUnit.DAYS.between(today, ae.getAssignment().getDeadline()));
        dto.setCompletedSimulationSessionId(ae.getCompletedSimulationSession() != null
                ? ae.getCompletedSimulationSession().getId() : null);
        return dto;
    }

    private boolean matchesAssignmentFilter(SimulationAssignmentDTO dto,
                                            String q,
                                            String status,
                                            Boolean hideCompleted,
                                            LocalDate deadlineFrom,
                                            LocalDate deadlineTo,
                                            LocalDate createdFrom,
                                            LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && dto.isFullyCompleted()) {
            return false;
        }
        if (!matchesAssignmentStatus(dto, status)) {
            return false;
        }
        if (!matchesText(q, dto.getAssignmentTitle(), dto.getCreatedByFullName(), dto.getScenarioTitle())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private boolean matchesEmployeeFilter(SimulationAssignmentEmployeeDTO dto,
                                          String q,
                                          String status,
                                          Boolean hideCompleted,
                                          LocalDate deadlineFrom,
                                          LocalDate deadlineTo,
                                          LocalDate createdFrom,
                                          LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && "COMPLETED".equals(dto.getStatusName())) {
            return false;
        }
        if (!matchesStatusName(dto.getStatusName(), status)) {
            return false;
        }
        if (!matchesText(q, dto.getEmployeeFullName())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private boolean matchesAssignedScenarioFilter(AssignedScenarioDTO dto,
                                                  String q,
                                                  String status,
                                                  Boolean hideCompleted,
                                                  LocalDate deadlineFrom,
                                                  LocalDate deadlineTo,
                                                  LocalDate createdFrom,
                                                  LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && "COMPLETED".equals(dto.getStatusName())) {
            return false;
        }
        if (!matchesStatusName(dto.getStatusName(), status)) {
            return false;
        }
        if (!matchesText(q, dto.getScenarioTitle(), dto.getScenarioDescription())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private Boolean defaultHideCompleted(Boolean hideCompleted) {
        return hideCompleted == null ? Boolean.TRUE : hideCompleted;
    }

    private boolean matchesAssignmentStatus(SimulationAssignmentDTO dto, String status) {
        AssignmentStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return true;
        }

        return switch (parsedStatus) {
            case PENDING -> dto.getTotalAssigned() - dto.getCompletedCount() - dto.getOverdueCount() - dto.getFailedCount() > 0;
            case COMPLETED -> dto.getCompletedCount() > 0;
            case FAILED -> dto.getFailedCount() > 0;
            case OVERDUE -> dto.getOverdueCount() > 0;
        };
    }

    private boolean matchesStatusName(String actualStatus, String status) {
        AssignmentStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return true;
        }
        return parsedStatus.name().equals(actualStatus);
    }

    private AssignmentStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return AssignmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean matchesText(String q, String... values) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String normalized = q.toLowerCase(Locale.ROOT).trim();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDateRange(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }

    private boolean matchesDateTimeRange(LocalDateTime value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        LocalDate localDate = value.toLocalDate();
        if (from != null && localDate.isBefore(from)) {
            return false;
        }
        return to == null || !localDate.isAfter(to);
    }
}
