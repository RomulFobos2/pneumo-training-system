package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LearningRecommendationDTO {
    private int totalQuestions;
    private int correctCount;
    private int wrongCount;
    private List<WeakTopicDTO> weakTopics = new ArrayList<>();
}
