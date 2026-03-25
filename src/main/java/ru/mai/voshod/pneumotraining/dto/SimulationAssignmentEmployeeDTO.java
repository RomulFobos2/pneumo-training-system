package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SimulationAssignmentEmployeeDTO {
    private Long id;
    private Long employeeId;
    private String employeeFullName;
    private String statusName;
    private String statusDisplayName;
    private Long completedSimulationSessionId;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer completedSteps;
    private Integer totalSteps;
    private String sessionStatus;
    private String sessionStatusDisplayName;
}
