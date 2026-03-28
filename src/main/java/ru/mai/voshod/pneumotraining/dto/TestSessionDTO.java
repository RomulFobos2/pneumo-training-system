package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestSessionDTO {
    private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime endTime;
    private LocalDateTime finishedAt;
    private Integer score;
    private Integer totalScore;
    private Double scorePercent;
    private Boolean isPassed;
    private String sessionStatusDisplayName;
    private String sessionStatusName;
    private Long employeeId;
    private String employeeFullName;
    private Long testId;
    private String testTitle;
    private boolean testIsExam;
    private Integer testPassingScore;
    private boolean allowBackNavigation;
    private int questionCount;
    private int answeredCount;
    private boolean hasAssignment;
}
