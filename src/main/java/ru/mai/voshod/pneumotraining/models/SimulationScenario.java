package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.ScenarioType;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_simulationScenario")
public class SimulationScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // минуты, 0 = без ограничения
    @Column(nullable = false)
    private Integer timeLimit = 0;

    @Column(nullable = false)
    private boolean availableWithoutAssignment = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScenarioType scenarioType = ScenarioType.NORMAL;

    // для аварийных сценариев — ссылка на штатный
    @ManyToOne
    @JoinColumn(name = "parent_scenario_id")
    @ToString.Exclude
    private SimulationScenario parentScenario;

    @OneToMany(mappedBy = "parentScenario")
    @ToString.Exclude
    private List<SimulationScenario> faultScenarios = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    @ToString.Exclude
    private List<ScenarioStep> steps = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "t_scenario_department",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @ToString.Exclude
    private List<Department> allowedDepartments = new ArrayList<>();
}
