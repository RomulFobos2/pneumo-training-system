package ru.mai.voshod.pneumotraining.service.employee.specialist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.mapper.SimulationSessionMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.SimulationSessionRepository;
import ru.mai.voshod.pneumotraining.service.employee.chief.SimulationScenarioService;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class SimulationService {

    private final SimulationSessionRepository sessionRepository;
    private final SimulationScenarioService scenarioService;
    private final ObjectMapper objectMapper;

    public SimulationService(SimulationSessionRepository sessionRepository,
                             SimulationScenarioService scenarioService,
                             ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.scenarioService = scenarioService;
        this.objectMapper = objectMapper;
    }

    // ========== Доступные сценарии ==========

    @Transactional(readOnly = true)
    public List<ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO> getAvailableScenarios(Employee employee) {
        return scenarioService.getAvailableScenariosForEmployee(employee);
    }

    // ========== Старт симуляции ==========

    @Transactional
    public Optional<Long> startSimulation(Long scenarioId, Employee employee) {
        log.info("Старт симуляции: scenarioId={}, employee={}", scenarioId, employee.getId());
        try {
            // Проверка на уже активную сессию по этому сценарию
            List<SimulationSession> existing = sessionRepository
                    .findByEmployeeIdAndScenarioIdAndSessionStatus(employee.getId(), scenarioId,
                            SimulationSessionStatus.IN_PROGRESS);
            if (!existing.isEmpty()) {
                log.info("Найдена активная сессия: id={}", existing.get(0).getId());
                return Optional.of(existing.get(0).getId());
            }

            // Загрузить сценарий со схемой
            var scenarioOpt = scenarioService.getScenarioEntity(scenarioId);
            if (scenarioOpt.isEmpty()) return Optional.empty();

            SimulationScenario scenario = scenarioOpt.get();
            if (!scenario.isActive()) {
                log.warn("Сценарий неактивен: id={}", scenarioId);
                return Optional.empty();
            }

            // Начальное состояние из элементов схемы
            Map<String, Boolean> initialState = new LinkedHashMap<>();
            MnemoSchema schema = scenario.getSchema();
            if (schema != null && schema.getElements() != null) {
                schema.getElements().forEach(el ->
                        initialState.put(el.getName(), el.isInitialState()));
            }

            SimulationSession session = new SimulationSession();
            session.setEmployee(employee);
            session.setScenario(scenario);
            session.setStartedAt(LocalDateTime.now());
            session.setSessionStatus(SimulationSessionStatus.IN_PROGRESS);
            session.setCurrentStep(1);
            session.setCompletedSteps(0);
            session.setTotalSteps(scenario.getSteps() != null ? scenario.getSteps().size() : 0);
            session.setCurrentState(objectMapper.writeValueAsString(initialState));

            if (scenario.getTimeLimit() != null && scenario.getTimeLimit() > 0) {
                session.setEndTime(session.getStartedAt().plusMinutes(scenario.getTimeLimit()));
            }

            sessionRepository.save(session);
            log.info("Сессия симуляции создана: id={}", session.getId());
            return Optional.of(session.getId());
        } catch (Exception e) {
            log.error("Ошибка старта симуляции", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Получение состояния ==========

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getSessionState(Long sessionId, Employee employee) {
        Optional<SimulationSession> sessionOpt = sessionRepository
                .findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) return Optional.empty();

        SimulationSession session = sessionOpt.get();

        // Проверка истечения таймера
        if (session.getSessionStatus() == SimulationSessionStatus.IN_PROGRESS && isExpired(session)) {
            return Optional.empty(); // Будет обработано через checkAndExpire
        }

        SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(session);
        dto.setCurrentState(session.getCurrentState());

        // Текущая инструкция
        if (session.getScenario() != null && session.getScenario().getSteps() != null) {
            session.getScenario().getSteps().stream()
                    .filter(s -> s.getStepNumber().equals(session.getCurrentStep()))
                    .findFirst()
                    .ifPresent(step -> dto.setCurrentInstruction(step.getInstructionText()));
        }

        return Optional.of(dto);
    }

    // ========== Переключение элемента ==========

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

            // Проверка таймера
            if (isExpired(session)) {
                expireSession(session);
                return Optional.of(Map.of("expired", true));
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
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Ошибка toggle элемента", e);
            return Optional.empty();
        }
    }

    // ========== Проверка шага (кумулятивная) ==========

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

            // Проверка таймера
            if (isExpired(session)) {
                expireSession(session);
                return Optional.of(Map.of("status", "expired"));
            }

            Map<String, Boolean> currentState = deserializeState(session.getCurrentState());
            List<ScenarioStep> steps = session.getScenario().getSteps();

            // Кумулятивная проверка: проверяем ВСЕ шаги от 1 до currentStep
            int currentStepNum = session.getCurrentStep();
            for (ScenarioStep step : steps) {
                if (step.getStepNumber() > currentStepNum) break;

                Map<String, Boolean> expected = deserializeState(step.getExpectedState());
                for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
                    Boolean actual = currentState.get(entry.getKey());
                    if (actual == null || !actual.equals(entry.getValue())) {
                        log.info("Шаг {}: ошибка на элементе {}, ожидалось={}, факт={}",
                                step.getStepNumber(), entry.getKey(), entry.getValue(), actual);
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("status", "wrong");
                        result.put("failedStep", step.getStepNumber());
                        result.put("failedElement", entry.getKey());
                        result.put("expected", entry.getValue());
                        result.put("actual", actual);
                        return Optional.of(result);
                    }
                }
            }

            // Все шаги до текущего верны — advance
            session.setCompletedSteps(currentStepNum);

            Map<String, Object> result = new LinkedHashMap<>();
            if (currentStepNum >= session.getTotalSteps()) {
                // Последний шаг — COMPLETED
                session.setSessionStatus(SimulationSessionStatus.COMPLETED);
                session.setFinishedAt(LocalDateTime.now());
                sessionRepository.save(session);
                log.info("Симуляция завершена успешно: sessionId={}", sessionId);
                result.put("status", "completed");
            } else {
                // Переход к следующему шагу
                session.setCurrentStep(currentStepNum + 1);
                sessionRepository.save(session);
                log.info("Переход к шагу {}: sessionId={}", currentStepNum + 1, sessionId);
                result.put("status", "advance");
                result.put("nextStep", currentStepNum + 1);

                // Инструкция следующего шага
                steps.stream()
                        .filter(s -> s.getStepNumber() == currentStepNum + 1)
                        .findFirst()
                        .ifPresent(s -> result.put("nextInstruction", s.getInstructionText()));
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Ошибка проверки шага", e);
            return Optional.empty();
        }
    }

    // ========== Проверка и expire таймера ==========

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

    // ========== Результат ==========

    @Transactional(readOnly = true)
    public Optional<SimulationSessionDTO> getResult(Long sessionId, Employee employee) {
        return sessionRepository.findByIdAndEmployeeId(sessionId, employee.getId())
                .map(session -> {
                    SimulationSessionDTO dto = SimulationSessionMapper.INSTANCE.toDTO(session);
                    dto.setScenarioTitle(session.getScenario().getTitle());
                    return dto;
                });
    }

    // ========== Мои результаты ==========

    @Transactional(readOnly = true)
    public List<SimulationSessionDTO> getMyResults(Employee employee) {
        return sessionRepository.findByEmployeeIdOrderByStartedAtDesc(employee.getId())
                .stream()
                .map(SimulationSessionMapper.INSTANCE::toDTO)
                .toList();
    }

    // ========== Вспомогательные ==========

    private boolean isExpired(SimulationSession session) {
        return session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime());
    }

    private void expireSession(SimulationSession session) {
        session.setSessionStatus(SimulationSessionStatus.EXPIRED);
        session.setFinishedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Сессия истекла: id={}", session.getId());
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
}
