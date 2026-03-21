package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Шаг сценария симуляции.
 *
 * Содержит инструкцию и ожидаемое состояние элементов (JSON).
 *
 * Связи:
 * - Сценарий (SimulationScenario)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_scenarioStep")
public class ScenarioStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер шага (порядок) */
    @NotNull
    @Column(nullable = false)
    private Integer stepNumber;

    /** Текст инструкции (например, "Откройте VP7, VP21-VP23") */
    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructionText;

    /**
     * Ожидаемое состояние элементов после выполнения шага.
     * JSON: {"elementId": true/false, ...}
     * Только элементы, которые ДОЛЖНЫ быть в определённом состоянии.
     */
    @Column(columnDefinition = "TEXT")
    private String expectedState;

    /** Сценарий, которому принадлежит шаг */
    @ManyToOne
    @JoinColumn(name = "scenario_id", nullable = false)
    @ToString.Exclude
    private SimulationScenario scenario;
}
