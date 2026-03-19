package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class TheorySectionDTO {
    private Long id;
    private String title;
    private int sortOrder;
    private String description;
    private int materialCount;
}
