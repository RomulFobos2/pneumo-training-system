package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class TheoryMaterialDTO {
    private Long id;
    private String title;
    private String content;
    private int sortOrder;
    private String materialTypeDisplayName;
    private String materialTypeName;
    private Long sectionId;
    private String sectionTitle;
}
