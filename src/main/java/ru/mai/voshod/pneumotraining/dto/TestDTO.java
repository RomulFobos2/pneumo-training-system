package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.List;

@Data
public class TestDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer passingScore;
    private boolean availableWithoutAssignment;
    private Long createdById;
    private String createdByFullName;
    private boolean allowBackNavigation;
    private int questionCount;
    private List<Long> departmentIds;
    private List<DepartmentDTO> allowedDepartments;
}
