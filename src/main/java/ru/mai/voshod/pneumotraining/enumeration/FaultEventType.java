package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Тип аварийного события на шаге сценария.
 */
@Getter
public enum FaultEventType {

    ELEMENT_FAILURE("Отказ элемента"),
    PRESSURE_ANOMALY("Аномалия давления"),
    OVERHEAT("Перегрев"),
    FALSE_ALARM("Ложное срабатывание");

    private final String displayName;

    FaultEventType(String displayName) {
        this.displayName = displayName;
    }
}
