package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TestSessionAnswerDTO {
    private Long id;
    private String answerText;
    private boolean isCorrect;
    private Long testQuestionId;
    private String questionText;
    private String questionTypeName;
    private Integer difficultyLevel;
    private Double earnedScoreRatio;
    private String scoreLevelDisplayName;
    private List<TestAnswerDTO> selectedAnswers = new ArrayList<>();
    private List<TestAnswerDTO> correctAnswers = new ArrayList<>();
}
