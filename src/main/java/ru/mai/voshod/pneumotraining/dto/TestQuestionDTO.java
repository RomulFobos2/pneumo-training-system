package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TestQuestionDTO {
    private Long id;
    private String questionText;
    private Integer difficultyLevel;
    private String questionTypeDisplayName;
    private String questionTypeName;
    private Long testId;
    private String testTitle;
    private Long theorySectionId;
    private String theorySectionTitle;
    private int answerCount;
    private List<TestAnswerDTO> answers = new ArrayList<>();
}
