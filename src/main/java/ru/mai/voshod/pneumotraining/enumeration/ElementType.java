package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

@Getter
public enum ElementType {

    VALVE("Клапан"),
    PUMP("Насос"),
    SWITCH("Переключатель"),
    SENSOR_PRESSURE("Датчик давления"),
    SENSOR_TEMPERATURE("Датчик температуры"),
    HEATER("Нагреватель"),
    LOCK("Блокировка"),
    LABEL("Надпись"),
    REDUCER("Редуктор"),
    SAFETY_VALVE("Предохранительный клапан"),
    FILTER("Фильтр"),
    CHECK_VALVE("Обратный клапан");

    private final String displayName;

    ElementType(String displayName) {
        this.displayName = displayName;
    }
}
