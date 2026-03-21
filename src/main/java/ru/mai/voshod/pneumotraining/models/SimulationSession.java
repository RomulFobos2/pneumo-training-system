package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;

import java.time.LocalDateTime;

/**
 * Сессия прохождения симуляции мнемосхемы.
 *
 * Создаётся при старте сценария специалистом.
 * Хранит текущее состояние элементов (JSON), прогресс и результат.
 *
 * Связи:
 * - Сотрудник (Employee)
 * - Сценарий (SimulationScenario)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_simulationSession")
public class SimulationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Время начала */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Время окончания (вычисляется: startedAt + timeLimit). Null = без ограничения */
    private LocalDateTime endTime;

    /** Фактическое время завершения */
    private LocalDateTime finishedAt;

    /** Текущий шаг (0-based) */
    @Column(nullable = false)
    private Integer currentStep = 0;

    /** Количество успешно пройденных шагов */
    @Column(nullable = false)
    private Integer completedSteps = 0;

    /** Общее количество шагов в сценарии */
    @Column(nullable = false)
    private Integer totalSteps = 0;

    /** Статус сессии */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimulationSessionStatus sessionStatus;

    /**
     * Текущее состояние всех элементов схемы.
     * JSON: {"elementId": true/false, ...}
     */
    @Column(columnDefinition = "TEXT")
    private String currentState;

    /** Сотрудник, проходящий симуляцию */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    /** Сценарий */
    @ManyToOne
    @JoinColumn(name = "scenario_id", nullable = false)
    @ToString.Exclude
    private SimulationScenario scenario;
}
