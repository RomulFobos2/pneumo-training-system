package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class MnemoSchemaDTO {
    private Long id;
    private String title;
    private String description;
    private Integer width;
    private Integer height;
    private Long createdById;
    private String createdByFullName;
    private int elementCount;
    private int connectionCount;
}
