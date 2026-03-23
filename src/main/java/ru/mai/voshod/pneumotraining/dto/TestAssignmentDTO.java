package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TestAssignmentDTO {
    private Long id;
    private Long testId;
    private String testTitle;
    private String assignmentTitle;
    private LocalDate deadline;
    private String createdByFullName;
    private LocalDateTime createdAt;
    private int totalAssigned;
    private int completedCount;
    private int overdueCount;
    private int failedCount;
    private boolean fullyCompleted;
}
