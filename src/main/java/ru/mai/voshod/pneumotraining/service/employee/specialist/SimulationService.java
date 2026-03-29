package ru.mai.voshod.pneumotraining.service.employee.specialist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.ElementType;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.SimulationSessionRepository;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationAssignmentService;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationScenarioService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class SimulationService {

    private final SimulationSessionRepository sessionRepository;
    private final SimulationScenarioService scenarioService;
    private final SimulationAssignmentService simulationAssignmentService;
    private final SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository;
    private final ObjectMapper objectMapper;

    public SimulationService(SimulationSessionRepository sessionRepository,
                             SimulationScenarioService scenarioService,
                             SimulationAssignmentService simulationAssignmentService,
                             SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository,
                             ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.scenarioService = scenarioService;
        this.simulationAssignmentService = simulationAssignmentService;
        this.simulationAssignmentEmployeeRepository = simulationAssignmentEmployeeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO> getAvailableScenarios(Employee employee) {
        return scenarioService.getAvailableScenariosForEmployee(employee);
    }

    @Transactional
    public Optional<Long> startSimulation(Long scenarioId, Employee employee) {
        log.info("Старт симуляции: scenarioId={}, employee={}", scenarioId, employee.getId());
        try {
            List<SimulationSession> existing = sessionRepository
                    .findByEmployeeIdAndScenarioIdAndSessionStatus(employee.getId(), scenarioId,
                            SimulationSessionStatus.IN_PROGRESS);
            if (!existing.isEmpty()) {
                log.info("Найдена активная сессия: id={}", existing.get(0).getId());
                return Optional.of(existing.get(0).getId());
            }

            var scenarioOpt = scenarioService.getScenarioEntity(scenarioId);
            if (scenarioOpt.isEmpty()) return Optional.empty();

            SimulationScenario scenario = scenarioOpt.get();
            if (!scenario.isActive()) {
                log.warn("Сценарий неактивен: id={}", scenarioId);
                return Optional.empty();
            }

            SimulationScenario actualScenario = pickScenarioVariant(scenario);

            Map<String, Boolean> initialState = new LinkedHashMap<>();
            MnemoSchema schema = actualScenario.getSchema();
            if (schema != null && schema.getElements() != null) {
                schema.getElements().stream()
                        .filter(el -> !isNonToggleable(el.getElementType()))
                        .forEach(el -> initialState.put(el.getName(), el.isInitialState()));
            }

            SimulationSession session = new SimulationSession();
            session.setEmployee(employee);
            session.setScenario(actualScenario);
            session.setStartedAt(LocalDateTime.now());
            session.setSessionStatus(SimulationSessionStatus.IN_PROGRESS);
            session.setCurrentStep(1);
            session.setCompletedSteps(0);
            session.setTotalSteps(actualScenario.getSteps() != null ? actualScenario.getSteps().size() : 0);
            session.setCurrentState(objectMapper.writeValueAsString(initialState));
            session.setLockedElements("[]");
            session.setWarnings("[]");
            session.setStepStartedAt(LocalDateTime.now());

            if (actualScenario.getTimeLimit() != null && actualScenario.getTimeLimit() > 0) {
                session.setEndTime(session.getStartedAt().plusMinutes(actualScenario.getTimeLimit()));
            }

            sessionRepository.save(session);

            applyFaultEvent(session, actualScenario.getSteps(), 1);
            sessionRepository.save(session);
            log.info("Сессия симуляции создана: id={}", session.getId());
            return Optional.of(session.getId());
        } catch (Exception e) {
            log.error("Ошибка старта симуляции", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getSessionState(Long sessionId, Employee employee) {
        Optional<SimulationSession> sessionOpt = sessionRepository
                .findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) return Optional.empty();

        SimulationSession session = sessionOpt.get();

        if (session.getSessionStatus() == SimulationSessionStatus.IN_PROGRESS && isExpired(session)) {
            return Optional.empty();
        }

        SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(session);
        dto.setCurrentState(session.getCurrentState());

        if (session.getScenario() != null && session.getScenario().getSteps() != null) {
            session.getScenario().getSteps().stream()
                    .filter(s -> s.getStepNumber().equals(session.getCurrentStep()))
                    .findFirst()
                    .ifPresent(step -> {
                        dto.setCurrentInstruction(step.getInstructionText());
                        dto.setCurrentStepTimeLimit(step.getStepTimeLimit());
                    });
        }

        return Optional.of(dto);
    }

    @Transactional
    public Optional<Map<String, Object>> toggleElement(Long sessionId, String elementName, Employee employee) {
        log.info("Toggle элемента: sessionId={}, element={}", sessionId, elementName);
        try {
            Optional<SimulationSession> sessionOpt = sessionRepository
                    .findByIdAndEmployeeId(sessionId, employee.getId());
            if (sessionOpt.isEmpty()) return Optional.empty();

            SimulationSession session = sessionOpt.get();
            if (session.getSessionStatus() != SimulationSessionStatus.IN_PROGRESS) {
                return Optional.empty();
            }

            if (isExpired(session)) {
                expireSession(session);
                return Optional.of(Map.of("expired", true));
            }

            MnemoSchema schema = session.getScenario().getSchema();
            if (schema != null) {
                Optional<SchemaElement> schemaElement = schema.getElements().stream()
                        .filter(e -> e.getName().equals(elementName))
                        .findFirst();
                if (schemaElement.isPresent() && isNonToggleable(schemaElement.get().getElementType())) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("notToggleable", true);
                    res.put("expired", false);
                    return Optional.of(res);
                }
            }

            List<String> locked = deserializeLockedElements(session.getLockedElements());
            if (locked.contains(elementName)) {
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("locked", true);
                res.put("expired", false);
                res.put("message", "Элемент неисправен");
                return Optional.of(res);
            }

            Map<String, Object> forbiddenResult = checkForbiddenAction(session, elementName);
            if (forbiddenResult != null && Boolean.TRUE.equals(forbiddenResult.get("failed"))) {
                return Optional.of(forbiddenResult);
            }

            Map<String, Boolean> state = deserializeState(session.getCurrentState());
            if (!state.containsKey(elementName)) {
                log.warn("Элемент не найден в состоянии: {}", elementName);
                return Optional.empty();
            }

            boolean newValue = !state.get(elementName);
            state.put(elementName, newValue);
            session.setCurrentState(objectMapper.writeValueAsString(state));
            sessionRepository.save(session);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("elementName", elementName);
            result.put("newState", newValue);
            result.put("expired", false);

            if (forbiddenResult != null) {
                result.put("warning", forbiddenResult.get("message"));
                result.put("penalty", forbiddenResult.get("penalty"));
            }

            return Optional.of(result);
        } catch (Exception e) {
            log.error("Ошибка toggle элемента", e);
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Map<String, Object>> checkStep(Long sessionId, Employee employee) {
        log.info("Проверка шага: sessionId={}", sessionId);
        try {
            Optional<SimulationSession> sessionOpt = sessionRepository
                    .findByIdAndEmployeeId(sessionId, employee.getId());
            if (sessionOpt.isEmpty()) return Optional.empty();

            SimulationSession session = sessionOpt.get();
            if (session.getSessionStatus() != SimulationSessionStatus.IN_PROGRESS) {
                return Optional.empty();
            }

            if (isExpired(session)) {
                recordStepResult(session, session.getCurrentStep(), false);
                expireSession(session);
                return Optional.of(Map.of("status", "expired"));
            }

            List<ScenarioStep> allSteps = session.getScenario().getSteps();
            if (isStepExpired(session, allSteps)) {
                int stepNum = session.getCurrentStep();
                recordStepResult(session, stepNum, false);
                session.setSessionStatus(SimulationSessionStatus.FAILED);
                session.setFinishedAt(LocalDateTime.now());
                sessionRepository.save(session);
                simulationAssignmentService.markAssignmentFailed(
                        employee.getId(), resolveNormalScenarioId(session.getScenario()), session);
                log.info("Время на шаг {} истекло: sessionId={}", stepNum, sessionId);
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("status", "step_expired");
                res.put("message", "Время на шаг истекло!");
                return Optional.of(res);
            }

            Map<String, Boolean> currentState = deserializeState(session.getCurrentState());
            List<ScenarioStep> steps = session.getScenario().getSteps();
            int currentStepNum = session.getCurrentStep();

            Map<String, Boolean> fullExpected = new LinkedHashMap<>();
            MnemoSchema schema = session.getScenario().getSchema();
            if (schema != null && schema.getElements() != null) {
                schema.getElements().stream()
                        .filter(el -> !isNonToggleable(el.getElementType()))
                        .forEach(el -> fullExpected.put(el.getName(), el.isInitialState()));
            }

            for (ScenarioStep step : steps) {
                if (step.getStepNumber() > currentStepNum) break;
                Map<String, Boolean> stepExpected = deserializeState(step.getExpectedState());
                fullExpected.putAll(stepExpected);
            }

            Set<String> nonToggleableNames = new HashSet<>();
            if (schema != null && schema.getElements() != null) {
                schema.getElements().stream()
                        .filter(el -> isNonToggleable(el.getElementType()))
                        .forEach(el -> nonToggleableNames.add(el.getName()));
            }

            for (Map.Entry<String, Boolean> entry : fullExpected.entrySet()) {
                if (nonToggleableNames.contains(entry.getKey())) continue;
                Boolean actual = currentState.get(entry.getKey());
                if (actual == null || !actual.equals(entry.getValue())) {
                    log.info("Ошибка на элементе {}: ожидалось={}, факт={}",
                            entry.getKey(), entry.getValue(), actual);
                    recordStepResult(session, currentStepNum, false);
                    sessionRepository.save(session);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "wrong");
                    result.put("failedElement", entry.getKey());
                    result.put("expected", entry.getValue());
                    result.put("actual", actual);
                    return Optional.of(result);
                }
            }

            recordStepResult(session, currentStepNum, true);
            session.setCompletedSteps(currentStepNum);

            Map<String, Object> result = new LinkedHashMap<>();
            if (currentStepNum >= session.getTotalSteps()) {
                session.setSessionStatus(SimulationSessionStatus.COMPLETED);
                session.setFinishedAt(LocalDateTime.now());
                sessionRepository.save(session);
                simulationAssignmentService.markAssignmentCompleted(
                        employee.getId(), resolveNormalScenarioId(session.getScenario()), session);
                log.info("Симуляция завершена успешно: sessionId={}", sessionId);
                result.put("status", "completed");
            } else {
                int nextStepNum = currentStepNum + 1;
                session.setCurrentStep(nextStepNum);
                session.setStepStartedAt(LocalDateTime.now());

                applyFaultEvent(session, steps, nextStepNum);
                sessionRepository.save(session);

                log.info("Переход к шагу {}: sessionId={}", nextStepNum, sessionId);
                result.put("status", "advance");
                result.put("nextStep", nextStepNum);
                result.put("lockedElements", session.getLockedElements());

                steps.stream()
                        .filter(s -> s.getStepNumber() == nextStepNum)
                        .findFirst()
                        .ifPresent(s -> {
                            result.put("nextInstruction", s.getInstructionText());
                            if (s.getFaultEvent() != null && !s.getFaultEvent().isBlank()) {
                                result.put("faultEvent", s.getFaultEvent());
                            }
                            if (s.getStepTimeLimit() != null && s.getStepTimeLimit() > 0) {
                                result.put("stepTimeLimit", s.getStepTimeLimit());
                                result.put("stepStartedAt", session.getStepStartedAt().toString());
                            }
                        });
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Ошибка проверки шага", e);
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<SimulationSessionDTO> checkAndExpire(Long sessionId, Employee employee) {
        Optional<SimulationSession> sessionOpt = sessionRepository
                .findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) return Optional.empty();

        SimulationSession session = sessionOpt.get();
        if (session.getSessionStatus() == SimulationSessionStatus.IN_PROGRESS && isExpired(session)) {
            expireSession(session);
        }
        return Optional.of(SimulationSessionMapper.INSTANCE.toDTO(session));
    }

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getResult(Long sessionId, Employee employee) {
        return sessionRepository.findByIdAndEmployeeId(sessionId, employee.getId())
                .map(session -> {
                    SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(session);
                    dto.setScenarioTitle(session.getScenario().getTitle());
                    return dto;
                });
    }

    @Transactional(readOnly = true)
    public List<SimulationSessionDTO> getMyResults(Employee employee) {
        List<Long> assignmentSessionIds = simulationAssignmentEmployeeRepository
                .findCompletedSimulationSessionIdsByEmployeeId(employee.getId());

        return sessionRepository.findByEmployeeIdOrderByStartedAtDesc(employee.getId())
                .stream()
                .filter(s -> s.getSessionStatus() != SimulationSessionStatus.IN_PROGRESS)
                .map(s -> {
                    SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(s);
                    dto.setHasAssignment(assignmentSessionIds.contains(s.getId()));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public String getCurrentStepFaultEvent(Long sessionId, Employee employee) {
        return sessionRepository.findByIdAndEmployeeId(sessionId, employee.getId())
                .filter(s -> s.getScenario() != null && s.getScenario().getSteps() != null)
                .flatMap(session -> session.getScenario().getSteps().stream()
                        .filter(s -> s.getStepNumber().equals(session.getCurrentStep()))
                        .findFirst()
                        .map(ScenarioStep::getFaultEvent))
                .orElse(null);
    }

    private Long resolveNormalScenarioId(SimulationScenario scenario) {
        if (scenario.getParentScenario() != null) {
            return scenario.getParentScenario().getId();
        }
        return scenario.getId();
    }

    private SimulationScenario pickScenarioVariant(SimulationScenario scenario) {
        if (scenario.getScenarioType() != ru.mai.voshod.pneumotraining.enumeration.ScenarioType.NORMAL) {
            return scenario;
        }
        List<SimulationScenario> faultVariants = scenarioService.getActiveFaultScenarios(scenario.getId());
        if (faultVariants.isEmpty()) {
            return scenario;
        }
        int pick = ThreadLocalRandom.current().nextInt(faultVariants.size() + 1);
        if (pick == 0) {
            log.info("Выбран штатный сценарий: id={}", scenario.getId());
            return scenario;
        }
        SimulationScenario fault = faultVariants.get(pick - 1);
        log.info("Выбран аварийный сценарий: id={} (вместо штатного id={})", fault.getId(), scenario.getId());
        return fault;
    }

    private boolean isExpired(SimulationSession session) {
        return session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime());
    }

    private void expireSession(SimulationSession session) {
        session.setSessionStatus(SimulationSessionStatus.EXPIRED);
        session.setFinishedAt(LocalDateTime.now());
        sessionRepository.save(session);
        simulationAssignmentService.markAssignmentFailed(
                session.getEmployee().getId(), resolveNormalScenarioId(session.getScenario()), session);
        log.info("Сессия истекла: id={}", session.getId());
    }

    private void recordStepResult(SimulationSession session, int stepNumber, boolean passed) {
        try {
            List<Map<String, Object>> results;
            if (session.getStepResults() != null && !session.getStepResults().isBlank()) {
                results = new ArrayList<>(objectMapper.readValue(session.getStepResults(),
                        new TypeReference<List<Map<String, Object>>>() {}));
            } else {
                results = new ArrayList<>();
            }
            String instruction = session.getScenario().getSteps().stream()
                    .filter(s -> s.getStepNumber() == stepNumber)
                    .findFirst()
                    .map(ScenarioStep::getInstructionText)
                    .orElse("Шаг " + stepNumber);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("step", stepNumber);
            entry.put("instruction", instruction);
            entry.put("passed", passed);
            entry.put("timestamp", LocalDateTime.now().toString());
            results.add(entry);
            session.setStepResults(objectMapper.writeValueAsString(results));
        } catch (Exception e) {
            log.error("Ошибка записи stepResults: {}", e.getMessage());
        }
    }

    private boolean isNonToggleable(ElementType type) {
        return type == ElementType.SENSOR_PRESSURE
                || type == ElementType.SENSOR_TEMPERATURE
                || type == ElementType.LABEL
                || type == ElementType.SAFETY_VALVE
                || type == ElementType.FILTER
                || type == ElementType.CHECK_VALVE;
    }

    private Map<String, Boolean> deserializeState(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации состояния: {}", json, e);
            return new LinkedHashMap<>();
        }
    }

    private void applyFaultEvent(SimulationSession session, List<ScenarioStep> steps, int stepNumber) {
        steps.stream()
                .filter(s -> s.getStepNumber() == stepNumber)
                .findFirst()
                .ifPresent(step -> {
                    if (step.getFaultEvent() != null && !step.getFaultEvent().isBlank()) {
                        try {
                            Map<String, Object> fault = objectMapper.readValue(step.getFaultEvent(),
                                    new TypeReference<>() {});
                            Boolean lockElement = (Boolean) fault.getOrDefault("lockElement", false);
                            String elementName = (String) fault.get("elementName");
                            if (Boolean.TRUE.equals(lockElement) && elementName != null) {
                                List<String> locked = deserializeLockedElements(session.getLockedElements());
                                if (!locked.contains(elementName)) {
                                    locked.add(elementName);
                                    session.setLockedElements(objectMapper.writeValueAsString(locked));
                                }
                            }
                            log.info("Аварийное событие на шаге {}: type={}, element={}",
                                    stepNumber, fault.get("type"), elementName);
                        } catch (Exception e) {
                            log.error("Ошибка применения аварийного события для шага {}", stepNumber, e);
                        }
                    }
                });
    }

    private List<String> deserializeLockedElements(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return new ArrayList<>();
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.error("Ошибка десериализации lockedElements: {}", json, e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> checkForbiddenAction(SimulationSession session, String elementName) {
        int currentStepNum = session.getCurrentStep();
        List<ScenarioStep> steps = session.getScenario().getSteps();
        ScenarioStep currentStep = steps.stream()
                .filter(s -> s.getStepNumber() == currentStepNum)
                .findFirst().orElse(null);
        if (currentStep == null || currentStep.getForbiddenActions() == null
                || currentStep.getForbiddenActions().isBlank()) {
            return null;
        }

        try {
            List<Map<String, String>> forbidden = objectMapper.readValue(
                    currentStep.getForbiddenActions(),
                    new TypeReference<>() {});

            Map<String, Boolean> state = deserializeState(session.getCurrentState());
            boolean currentlyOn = Boolean.TRUE.equals(state.get(elementName));
            String attemptedAction = currentlyOn ? "off" : "on";

            for (Map<String, String> rule : forbidden) {
                if (elementName.equals(rule.get("elementName"))
                        && attemptedAction.equals(rule.get("action"))) {

                    String penalty = rule.get("penalty");
                    String message = rule.get("message");

                    if ("FAIL".equals(penalty)) {
                        session.setSessionStatus(SimulationSessionStatus.FAILED);
                        session.setFinishedAt(LocalDateTime.now());
                        recordStepResult(session, currentStepNum, false);
                        sessionRepository.save(session);
                        simulationAssignmentService.markAssignmentFailed(
                                session.getEmployee().getId(), resolveNormalScenarioId(session.getScenario()), session);
                        log.info("Запрещённое действие FAIL: element={}, action={}", elementName, attemptedAction);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("forbidden", true);
                        result.put("failed", true);
                        result.put("penalty", "FAIL");
                        result.put("message", message);
                        result.put("expired", false);
                        return result;
                    } else if ("WARNING".equals(penalty)) {
                        addWarning(session, currentStepNum, message);
                        sessionRepository.save(session);
                        log.info("Запрещённое действие WARNING: element={}, message={}", elementName, message);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("penalty", "WARNING");
                        result.put("message", message);
                        return result;
                    } else if ("TIME_PENALTY".equals(penalty)) {
                        applyTimePenalty(session);
                        addWarning(session, currentStepNum, message + " (штраф −30 сек)");
                        sessionRepository.save(session);
                        log.info("Запрещённое действие TIME_PENALTY: element={}", elementName);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("penalty", "TIME_PENALTY");
                        result.put("message", message);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка проверки запрещённых действий", e);
        }
        return null;
    }

    private void addWarning(SimulationSession session, int step, String message) {
        try {
            List<Map<String, Object>> warnings;
            if (session.getWarnings() != null && !session.getWarnings().isBlank()
                    && !session.getWarnings().equals("[]")) {
                warnings = new ArrayList<>(objectMapper.readValue(session.getWarnings(),
                        new TypeReference<>() {}));
            } else {
                warnings = new ArrayList<>();
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("step", step);
            entry.put("message", message);
            entry.put("timestamp", LocalDateTime.now().toString());
            warnings.add(entry);
            session.setWarnings(objectMapper.writeValueAsString(warnings));
        } catch (Exception e) {
            log.error("Ошибка добавления предупреждения", e);
        }
    }

    private void applyTimePenalty(SimulationSession session) {
        if (session.getEndTime() != null) {
            session.setEndTime(session.getEndTime().minusSeconds(30));
            log.info("TIME_PENALTY: endTime уменьшен на 30 сек, sessionId={}", session.getId());
        }
    }

    private boolean isStepExpired(SimulationSession session, List<ScenarioStep> steps) {
        if (session.getStepStartedAt() == null) return false;
        return steps.stream()
                .filter(s -> s.getStepNumber().equals(session.getCurrentStep()))
                .findFirst()
                .map(step -> step.getStepTimeLimit() != null
                        && step.getStepTimeLimit() > 0
                        && LocalDateTime.now().isAfter(
                        session.getStepStartedAt().plusSeconds(step.getStepTimeLimit())))
                .orElse(false);
    }
}
