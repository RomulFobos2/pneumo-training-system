package ru.mai.voshod.pneumotraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Составной DTO для AJAX save/load данных схемы (элементы + соединения).
 */
@Data
public class SchemaDataDTO {
    private Integer width;
    private Integer height;
    private List<SchemaElementDTO> elements = new ArrayList<>();
    private List<SchemaConnectionDTO> connections = new ArrayList<>();
}
