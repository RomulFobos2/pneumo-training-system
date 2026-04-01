package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.ScenarioStepDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO;
import ru.mai.voshod.pneumotraining.enumeration.ScenarioType;
import ru.mai.voshod.pneumotraining.mapper.DepartmentMapper;
import ru.mai.voshod.pneumotraining.mapper.ScenarioStepMapper;
import ru.mai.voshod.pneumotraining.mapper.SimulationScenarioMapper;
import ru.mai.voshod.pneumotraining.models.Department;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.MnemoSchema;
import ru.mai.voshod.pneumotraining.models.ScenarioStep;
import ru.mai.voshod.pneumotraining.models.SimulationScenario;
import ru.mai.voshod.pneumotraining.repo.DepartmentRepository;
import ru.mai.voshod.pneumotraining.repo.MnemoSchemaRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationScenarioRepository;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SimulationScenarioService {

    private final SimulationScenarioRepository scenarioRepository;
    private final MnemoSchemaRepository schemaRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;

    public SimulationScenarioService(SimulationScenarioRepository scenarioRepository,
                                     MnemoSchemaRepository schemaRepository,
                                     DepartmentRepository departmentRepository,
                                     DepartmentService departmentService) {
        this.scenarioRepository = scenarioRepository;
        this.schemaRepository = schemaRepository;
        this.departmentRepository = departmentRepository;
        this.departmentService = departmentService;
    }

    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getAllScenarios() {
        return scenarioRepository.findByScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL).stream()
                .map(this::toScenarioTreeDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationScenarioDTO> getScenarioById(Long id) {
        return scenarioRepository.findById(id).map(this::toScenarioDTO);
    }

    @Transactional(readOnly = true)
    public List<ScenarioStepDTO> getStepsByScenarioId(Long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .map(s -> ScenarioStepMapper.INSTANCE.toDTOList(s.getSteps()))
                .orElse(List.of());
    }

    @Transactional
    public Optional<Long> saveScenario(String title, String description, Integer timeLimit,
                                       Long schemaId, boolean availableWithoutAssignment, List<Long> departmentIds,
                                       ScenarioType scenarioType, Long parentScenarioId,
                                       Employee createdBy) {
        log.info("Создание сценария: title={}, schemaId={}, type={}", title, schemaId, scenarioType);
        try {
            Optional<MnemoSchema> schemaOpt = schemaRepository.findById(schemaId);
            if (schemaOpt.isEmpty()) {
                log.warn("Схема не найдена: id={}", schemaId);
                return Optional.empty();
            }

            SimulationScenario scenario = new SimulationScenario();
            scenario.setTitle(title);
            scenario.setDescription(description);
            scenario.setTimeLimit(timeLimit != null ? timeLimit : 0);
            scenario.setSchema(schemaOpt.get());
            scenario.setCreatedBy(createdBy);

            if (!applyAccessConfiguration(scenario, scenarioType, parentScenarioId,
                    availableWithoutAssignment, departmentIds)) {
                return Optional.empty();
            }

            scenarioRepository.save(scenario);
            log.info("Сценарий создан: id={}", scenario.getId());
            return Optional.of(scenario.getId());
        } catch (Exception e) {
            log.error("Ошибка создания сценария", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editScenario(Long id, String title, String description, Integer timeLimit,
                                       Long schemaId, boolean availableWithoutAssignment, List<Long> departmentIds,
                                       ScenarioType scenarioType, Long parentScenarioId) {
        log.info("Редактирование сценария: id={}", id);
        try {
            Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(id);
            if (scenarioOpt.isEmpty()) {
                return Optional.empty();
            }

            SimulationScenario scenario = scenarioOpt.get();
            scenario.setTitle(title);
            scenario.setDescription(description);
            scenario.setTimeLimit(timeLimit != null ? timeLimit : 0);

            if (schemaId != null) {
                schemaRepository.findById(schemaId).ifPresent(scenario::setSchema);
            }

            if (!applyAccessConfiguration(scenario, scenarioType, parentScenarioId,
                    availableWithoutAssignment, departmentIds)) {
                return Optional.empty();
            }

            scenarioRepository.save(scenario);
            log.info("Сценарий обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка редактирования сценария", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteScenario(Long id) {
        log.info("Удаление сценария: id={}", id);
        try {
            Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(id);
            if (scenarioOpt.isEmpty()) {
                return false;
            }
            scenarioRepository.delete(scenarioOpt.get());
            log.info("Сценарий удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка удаления сценария", e);
            return false;
        }
    }

    @Transactional
    public boolean saveSteps(Long scenarioId, List<ScenarioStepDTO> stepsDTO) {
        log.info("Сохранение шагов сценария: id={}, steps={}", scenarioId, stepsDTO.size());
        try {
            Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(scenarioId);
            if (scenarioOpt.isEmpty()) {
                return false;
            }

            SimulationScenario scenario = scenarioOpt.get();
            scenario.getSteps().clear();

            for (ScenarioStepDTO dto : stepsDTO) {
                ScenarioStep step = new ScenarioStep();
                step.setStepNumber(dto.getStepNumber());
                step.setInstructionText(dto.getInstructionText());
                step.setExpectedState(dto.getExpectedState());
                step.setFaultEvent(dto.getFaultEvent());
                step.setForbiddenActions(dto.getForbiddenActions());
                step.setStepTimeLimit(dto.getStepTimeLimit());
                step.setScenario(scenario);
                scenario.getSteps().add(step);
            }

            scenarioRepository.save(scenario);
            log.info("Шаги сценария сохранены: id={}", scenarioId);
            return true;
        } catch (Exception e) {
            log.error("Ошибка сохранения шагов", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getAvailableScenariosForEmployee(Employee employee) {
        if (employee.getDepartment() == null) {
            return List.of();
        }

        List<SimulationScenario> allFreeNormal = scenarioRepository
                .findByAvailableWithoutAssignmentTrueAndScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL);

        return allFreeNormal.stream()
                .filter(s -> isDepartmentAllowedForScenario(s, employee.getDepartment().getId()))
                .map(this::toScenarioDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getScenariosForDepartment(Long departmentId) {
        List<SimulationScenario> normalScenarios =
                scenarioRepository.findByAvailableWithoutAssignmentFalseAndScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL);
        return normalScenarios.stream()
                .filter(s -> isDepartmentAllowedForScenario(s, departmentId))
                .map(this::toScenarioDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getNormalScenarios() {
        return scenarioRepository.findByScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL).stream()
                .map(this::toScenarioDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationScenario> getScenarioEntity(Long id) {
        return scenarioRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<SimulationScenario> getFaultScenarios(Long parentScenarioId) {
        return scenarioRepository.findByParentScenarioIdOrderByTitleAsc(parentScenarioId);
    }

    @Transactional(readOnly = true)
    public boolean isAvailableWithoutAssignment(SimulationScenario scenario) {
        return getAccessSourceScenario(scenario).isAvailableWithoutAssignment();
    }

    @Transactional(readOnly = true)
    public boolean isDepartmentAllowedForScenario(SimulationScenario scenario, Long departmentId) {
        if (departmentId == null) {
            return false;
        }
        List<Long> ancestorIds = departmentService.getAncestorIds(departmentId);
        return getEffectiveAllowedDepartments(scenario).stream()
                .anyMatch(d -> ancestorIds.contains(d.getId()));
    }

    @Transactional(readOnly = true)
    public List<Department> getEffectiveAllowedDepartments(SimulationScenario scenario) {
        return new ArrayList<>(getAccessSourceScenario(scenario).getAllowedDepartments());
    }

    @Transactional(readOnly = true)
    public SimulationScenario getAccessSourceScenario(SimulationScenario scenario) {
        if (scenario.getScenarioType() == ScenarioType.FAULT && scenario.getParentScenario() != null) {
            return scenario.getParentScenario();
        }
        return scenario;
    }

    private boolean applyAccessConfiguration(SimulationScenario scenario,
                                             ScenarioType scenarioType,
                                             Long parentScenarioId,
                                             boolean availableWithoutAssignment,
                                             List<Long> departmentIds) {
        ScenarioType resolvedType = scenarioType != null ? scenarioType : ScenarioType.NORMAL;
        scenario.setScenarioType(resolvedType);

        if (resolvedType == ScenarioType.FAULT) {
            Optional<SimulationScenario> parentOpt = loadValidParentScenario(parentScenarioId, scenario.getId());
            if (parentOpt.isEmpty()) {
                log.warn("Для аварийного сценария не найден корректный штатный родитель: parentId={}", parentScenarioId);
                return false;
            }

            SimulationScenario parent = parentOpt.get();
            scenario.setParentScenario(parent);
            scenario.setAvailableWithoutAssignment(parent.isAvailableWithoutAssignment());
            scenario.setAllowedDepartments(new ArrayList<>(parent.getAllowedDepartments()));
            return true;
        }

        scenario.setParentScenario(null);
        scenario.setAvailableWithoutAssignment(availableWithoutAssignment);
        setAllowedDepartments(scenario, departmentIds);
        return true;
    }

    private Optional<SimulationScenario> loadValidParentScenario(Long parentScenarioId, Long currentScenarioId) {
        if (parentScenarioId == null) {
            return Optional.empty();
        }
        return scenarioRepository.findById(parentScenarioId)
                .filter(parent -> parent.getScenarioType() == ScenarioType.NORMAL)
                .filter(parent -> currentScenarioId == null || !parent.getId().equals(currentScenarioId));
    }

    private void setAllowedDepartments(SimulationScenario scenario, List<Long> departmentIds) {
        if (departmentIds != null && !departmentIds.isEmpty()) {
            scenario.setAllowedDepartments(new ArrayList<>(departmentRepository.findAllById(departmentIds)));
        } else {
            scenario.setAllowedDepartments(new ArrayList<>());
        }
    }

    private SimulationScenarioDTO toScenarioDTO(SimulationScenario scenario) {
        SimulationScenarioDTO dto = SimulationScenarioMapper.INSTANCE.toDTO(scenario);
        dto.setStepCount(scenario.getSteps() != null ? scenario.getSteps().size() : 0);
        dto.setFaultScenarioCount(scenario.getFaultScenarios() != null ? scenario.getFaultScenarios().size() : 0);

        List<Department> effectiveDepartments = getEffectiveAllowedDepartments(scenario);
        dto.setDepartmentIds(effectiveDepartments.stream().map(Department::getId).toList());
        dto.setAllowedDepartments(DepartmentMapper.INSTANCE.toDTOList(effectiveDepartments));
        dto.setAvailableWithoutAssignment(isAvailableWithoutAssignment(scenario));

        return dto;
    }

    private SimulationScenarioDTO toScenarioTreeDTO(SimulationScenario scenario) {
        SimulationScenarioDTO dto = toScenarioDTO(scenario);
        if (scenario.getScenarioType() == ScenarioType.NORMAL && scenario.getFaultScenarios() != null) {
            dto.setFaultScenariosList(scenario.getFaultScenarios().stream()
                    .sorted((left, right) -> left.getTitle().compareToIgnoreCase(right.getTitle()))
                    .map(this::toScenarioDTO)
                    .toList());
        } else {
            dto.setFaultScenariosList(List.of());
        }
        return dto;
    }
}
