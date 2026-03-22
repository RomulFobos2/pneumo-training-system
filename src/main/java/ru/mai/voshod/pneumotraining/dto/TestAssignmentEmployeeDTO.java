package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class TestAssignmentEmployeeDTO {
    private Long id;
    private Long employeeId;
    private String employeeFullName;
    private String statusName;
    private String statusDisplayName;
    private Long completedSessionId;
    private Long completedSimulationSessionId;
}
