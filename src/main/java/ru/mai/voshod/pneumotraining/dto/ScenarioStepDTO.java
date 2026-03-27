package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class ScenarioStepDTO {
    private Long id;
    private Integer stepNumber;
    private String instructionText;
    private String expectedState;
    private String faultEvent;
    private String forbiddenActions;
    private Integer stepTimeLimit;
}
