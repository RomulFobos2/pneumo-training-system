package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssignedScenarioDTO {
    private Long assignmentEmployeeId;
    private Long scenarioId;
    private String scenarioTitle;
    private String scenarioDescription;
    private LocalDate deadline;
    private String statusName;
    private String statusDisplayName;
    private long daysUntilDeadline;
}
