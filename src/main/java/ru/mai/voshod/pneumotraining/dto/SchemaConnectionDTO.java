package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class SchemaConnectionDTO {
    private Long id;
    private Long sourceElementId;
    private Long targetElementId;
    private String pathData;
}
