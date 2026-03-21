package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentDTO {
    private Long id;
    private String name;
    private String description;
    private int employeeCount;
    private Long parentId;
    private String parentName;
    private int level;
    private List<DepartmentDTO> children = new ArrayList<>();
}
