package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class TestDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer passingScore;
    private boolean isExam;
    private boolean isActive;
    private Long createdById;
    private String createdByFullName;
    private int questionCount;
}
