package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.DepartmentDTO;
import ru.mai.voshod.pneumotraining.dto.ScenarioStepDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO;
import ru.mai.voshod.pneumotraining.enumeration.ScenarioType;
import ru.mai.voshod.pneumotraining.mapper.DepartmentMapper;
import ru.mai.voshod.pneumotraining.mapper.ScenarioStepMapper;
import ru.mai.voshod.pneumotraining.mapper.SimulationScenarioMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.DepartmentRepository;
import ru.mai.voshod.pneumotraining.repo.MnemoSchemaRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationScenarioRepository;
import ru.mai.voshod.pneumotraining.service.employee.admin.DepartmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return scenarioRepository.findAllByOrderByTitleAsc().stream()
                .map(this::toScenarioDTO).toList();
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
                                        Long schemaId, boolean isActive, List<Long> departmentIds,
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
            scenario.setActive(isActive);
            scenario.setSchema(schemaOpt.get());
            scenario.setCreatedBy(createdBy);
            scenario.setScenarioType(scenarioType != null ? scenarioType : ScenarioType.NORMAL);

            if (scenarioType == ScenarioType.FAULT && parentScenarioId != null) {
                scenarioRepository.findById(parentScenarioId).ifPresent(scenario::setParentScenario);
            }

            setAllowedDepartments(scenario, departmentIds);
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
                                        Long schemaId, boolean isActive, List<Long> departmentIds,
                                        ScenarioType scenarioType, Long parentScenarioId) {
        log.info("Редактирование сценария: id={}", id);
        try {
            Optional<SimulationScenario> scenarioOpt = scenarioRepository.findById(id);
            if (scenarioOpt.isEmpty()) return Optional.empty();

            SimulationScenario scenario = scenarioOpt.get();
            scenario.setTitle(title);
            scenario.setDescription(description);
            scenario.setTimeLimit(timeLimit != null ? timeLimit : 0);
            scenario.setActive(isActive);
            scenario.setScenarioType(scenarioType != null ? scenarioType : ScenarioType.NORMAL);

            if (scenarioType == ScenarioType.FAULT && parentScenarioId != null) {
                scenarioRepository.findById(parentScenarioId).ifPresent(scenario::setParentScenario);
            } else {
                scenario.setParentScenario(null);
            }

            if (schemaId != null) {
                schemaRepository.findById(schemaId).ifPresent(scenario::setSchema);
            }

            setAllowedDepartments(scenario, departmentIds);
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
            if (scenarioOpt.isEmpty()) return false;
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
            if (scenarioOpt.isEmpty()) return false;

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
        if (employee.getDepartment() == null) return List.of();

        List<Long> ancestorIds = departmentService.getAncestorIds(employee.getDepartment().getId());
        List<SimulationScenario> allActiveNormal =
                scenarioRepository.findByIsActiveTrueAndScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL);

        return allActiveNormal.stream()
                .filter(s -> s.getAllowedDepartments().stream()
                        .anyMatch(d -> ancestorIds.contains(d.getId())))
                .map(this::toScenarioDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimulationScenario> getScenarioEntity(Long id) {
        return scenarioRepository.findById(id);
    }

    /**
     * Сценарии для назначения: только НЕ «доступные без назначения» (isActive=false),
     * штатные, привязанные к подразделению.
     */
    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getScenariosForDepartment(Long departmentId) {
        List<Long> ancestorIds = departmentService.getAncestorIds(departmentId);
        List<SimulationScenario> normalScenarios =
                scenarioRepository.findByScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL);
        return normalScenarios.stream()
                .filter(s -> !s.isActive())
                .filter(s -> s.getAllowedDepartments().stream()
                        .anyMatch(d -> ancestorIds.contains(d.getId())))
                .map(this::toScenarioDTO)
                .toList();
    }

    /** Получить все штатные сценарии (для выбора родителя в форме аварийного сценария) */
    @Transactional(readOnly = true)
    public List<SimulationScenarioDTO> getNormalScenarios() {
        return scenarioRepository.findByScenarioTypeOrderByTitleAsc(ScenarioType.NORMAL)
                .stream().map(this::toScenarioDTO).toList();
    }

    /** Получить активные аварийные сценарии для штатного */
    @Transactional(readOnly = true)
    public List<SimulationScenario> getActiveFaultScenarios(Long parentScenarioId) {
        return scenarioRepository.findByParentScenarioIdAndIsActiveTrue(parentScenarioId);
    }

    // ========== Вспомогательные ==========

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
        dto.setDepartmentIds(scenario.getAllowedDepartments().stream()
                .map(Department::getId).collect(Collectors.toList()));
        dto.setAllowedDepartments(DepartmentMapper.INSTANCE.toDTOList(scenario.getAllowedDepartments()));
        dto.setFaultScenarioCount(scenario.getFaultScenarios() != null ? scenario.getFaultScenarios().size() : 0);
        return dto;
    }
}
