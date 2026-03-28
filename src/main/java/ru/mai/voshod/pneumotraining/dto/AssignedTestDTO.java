package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AssignedTestDTO {
    private Long assignmentEmployeeId;
    private Long testId;
    private String testTitle;
    private String testDescription;
    private int questionCount;
    private int timeLimit;
    private int passingScore;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private String statusName;
    private String statusDisplayName;
    private long daysUntilDeadline;
    private Long completedSessionId;
}
