package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AssignedScenarioDTO {
    private Long assignmentEmployeeId;
    private Long scenarioId;
    private String scenarioTitle;
    private String scenarioDescription;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private String statusName;
    private String statusDisplayName;
    private long daysUntilDeadline;
    private Long completedSimulationSessionId;
}
