package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_simulationSession")
public class SimulationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    // startedAt + timeLimit; null = без ограничения
    private LocalDateTime endTime;

    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private Integer currentStep = 0;

    @Column(nullable = false)
    private Integer completedSteps = 0;

    @Column(nullable = false)
    private Integer totalSteps = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimulationSessionStatus sessionStatus;

    // JSON: {"elementName": true/false, ...}
    @Column(columnDefinition = "TEXT")
    private String currentState;

    // JSON: [{"step":1,"instruction":"...","passed":true,"timestamp":"..."}, ...]
    @Column(columnDefinition = "TEXT")
    private String stepResults;

    // JSON: ["VP5","NR2"]
    @Column(columnDefinition = "TEXT")
    private String lockedElements;

    // JSON: [{"step":2,"message":"...","timestamp":"..."}]
    @Column(columnDefinition = "TEXT")
    private String warnings;

    private LocalDateTime stepStartedAt;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "scenario_id", nullable = false)
    @ToString.Exclude
    private SimulationScenario scenario;
}
