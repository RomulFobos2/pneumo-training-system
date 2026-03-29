package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_scenarioStep")
public class ScenarioStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Integer stepNumber;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructionText;

    // JSON: {"elementName": true/false, ...}
    @Column(columnDefinition = "TEXT")
    private String expectedState;

    // JSON: {"type":"ELEMENT_FAILURE","elementName":"VP5","message":"...","lockElement":true}
    @Column(columnDefinition = "TEXT")
    private String faultEvent;

    // JSON: [{"elementName":"VP5","action":"on","penalty":"FAIL","message":"..."}]
    @Column(columnDefinition = "TEXT")
    private String forbiddenActions;

    // секунды; null = без ограничения
    private Integer stepTimeLimit;

    @ManyToOne
    @JoinColumn(name = "scenario_id", nullable = false)
    @ToString.Exclude
    private SimulationScenario scenario;
}
