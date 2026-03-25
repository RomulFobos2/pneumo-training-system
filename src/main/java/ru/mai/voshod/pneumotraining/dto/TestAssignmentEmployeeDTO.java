package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TestAssignmentEmployeeDTO {
    private Long id;
    private Long employeeId;
    private String employeeFullName;
    private String statusName;
    private String statusDisplayName;
    private Long completedSessionId;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer score;
    private Integer totalScore;
    private Double scorePercent;
    private Boolean passed;
    private String sessionStatusName;
    private String sessionStatusDisplayName;
}
