package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class TestAnswerDTO {
    private Long id;
    private String answerText;
    private boolean isCorrect;
    private Integer sortOrder;
    private String matchTarget;
    private Long questionId;
}
