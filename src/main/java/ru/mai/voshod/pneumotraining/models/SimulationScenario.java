package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий симуляции мнемосхемы.
 *
 * Определяет пошаговую последовательность действий на конкретной схеме.
 * Создаётся начальником группы (Chief).
 *
 * Связи:
 * - Схема (MnemoSchema)
 * - Создатель (Employee)
 * - Шаги сценария (ScenarioStep)
 * - Допущенные подразделения (Department)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_simulationScenario")
public class SimulationScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название сценария */
    @NotNull
    @Column(nullable = false, length = 255)
    private String title;

    /** Описание сценария */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Ограничение по времени (в минутах), 0 = без ограничения */
    @Column(nullable = false)
    private Integer timeLimit = 0;

    /** Активен ли сценарий (доступен для прохождения) */
    @Column(nullable = false)
    private boolean isActive = false;

    /** Схема, к которой привязан сценарий */
    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;

    /** Создатель сценария */
    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    /** Шаги сценария */
    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    @ToString.Exclude
    private List<ScenarioStep> steps = new ArrayList<>();

    /** Подразделения, которым доступен сценарий */
    @ManyToMany
    @JoinTable(
            name = "t_scenario_department",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @ToString.Exclude
    private List<Department> allowedDepartments = new ArrayList<>();
}
