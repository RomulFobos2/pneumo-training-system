package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_simulationAssignment")
public class SimulationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "scenario_id")
    @ToString.Exclude
    private SimulationScenario scenario;

    @NotNull
    @Column(nullable = false)
    private LocalDate deadline;

    @ManyToOne
    @JoinColumn(name = "created_by_id", nullable = false)
    @ToString.Exclude
    private Employee createdBy;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SimulationAssignmentEmployee> assignedEmployees = new ArrayList<>();

    public SimulationAssignment(SimulationScenario scenario, LocalDate deadline, Employee createdBy) {
        this.scenario = scenario;
        this.deadline = deadline;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public String getAssignmentTitle() {
        return scenario != null ? scenario.getTitle() : "—";
    }
}
