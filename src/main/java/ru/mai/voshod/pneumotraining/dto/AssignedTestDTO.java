package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssignedTestDTO {
    private Long assignmentEmployeeId;
    private Long testId;
    private String testTitle;
    private String testDescription;
    private int questionCount;
    private int timeLimit;
    private int passingScore;
    private boolean isExam;
    private LocalDate deadline;
    private String statusName;
    private String statusDisplayName;
    private long daysUntilDeadline;
}
