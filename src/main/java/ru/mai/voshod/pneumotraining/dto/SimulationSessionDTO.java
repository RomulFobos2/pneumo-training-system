package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@SuppressWarnings("unused")
public class SimulationSessionDTO {
    private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime endTime;
    private LocalDateTime finishedAt;
    private Integer currentStep;
    private Integer completedSteps;
    private Integer totalSteps;
    private String sessionStatus;
    private String sessionStatusDisplayName;
    private Long employeeId;
    private String employeeFullName;
    private Long scenarioId;
    private String scenarioTitle;
    private String currentState;
    private String currentInstruction;
    private Long schemaId;
    private String stepResults;
    private String lockedElements;
    private String warnings;
    private LocalDateTime stepStartedAt;
    private Integer currentStepTimeLimit;
    private boolean hasAssignment;
}
