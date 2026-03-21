package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class DepartmentDTO {
    private Long id;
    private String name;
    private String description;
    private int employeeCount;
}
