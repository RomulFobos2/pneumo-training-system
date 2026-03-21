package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

@Data
public class SchemaElementDTO {
    private Long id;
    private String name;
    private String elementType;
    private Double posX;
    private Double posY;
    private Double width;
    private Double height;
    private boolean initialState;
    private Integer rotation;
}
