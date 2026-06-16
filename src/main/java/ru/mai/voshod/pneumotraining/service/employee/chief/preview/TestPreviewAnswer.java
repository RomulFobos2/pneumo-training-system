package ru.mai.voshod.pneumotraining.service.employee.chief.preview;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TestPreviewAnswer implements Serializable {

    private Long questionId;
    private List<Long> selectedAnswerIds = new ArrayList<>();
    private String answerText;
    private Double earnedScoreRatio;
    private boolean isCorrect;
}
