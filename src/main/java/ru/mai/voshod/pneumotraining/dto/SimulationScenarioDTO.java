package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.List;

@Data
public class SimulationScenarioDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private boolean availableWithoutAssignment;
    private Long schemaId;
    private String schemaTitle;
    private Long createdById;
    private String createdByFullName;
    private int stepCount;
    private List<Long> departmentIds;
    private List<DepartmentDTO> allowedDepartments;
    private String scenarioTypeName;
    private String scenarioTypeDisplayName;
    private Long parentScenarioId;
    private String parentScenarioTitle;
    private int faultScenarioCount;
    private List<SimulationScenarioDTO> faultScenariosList;
}
