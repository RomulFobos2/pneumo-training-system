package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class SimulationAssignmentEmployeeDTO {
    private Long id;
    private Long employeeId;
    private String employeeFullName;
    private String statusName;
    private String statusDisplayName;
    private Long completedSimulationSessionId;
}
