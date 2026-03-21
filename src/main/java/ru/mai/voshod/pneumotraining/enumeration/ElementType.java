package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Тип элемента мнемосхемы
 */
@Getter
public enum ElementType {

    VALVE("Клапан"),
    PUMP("Насос"),
    SWITCH("Переключатель"),
    SENSOR_PRESSURE("Датчик давления"),
    SENSOR_TEMPERATURE("Датчик температуры"),
    HEATER("Нагреватель"),
    LOCK("Блокировка"),
    LABEL("Надпись");

    private final String displayName;

    ElementType(String displayName) {
        this.displayName = displayName;
    }
}
