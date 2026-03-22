package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WeakTopicDTO {
    private Long sectionId;
    private String sectionTitle;
    private int wrongCount;
    private int totalInSection;
    private List<TheoryMaterialDTO> materials = new ArrayList<>();
    private List<TestDTO> suggestedTests = new ArrayList<>();
}
