package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

@Getter
public enum SimulationSessionStatus {

    IN_PROGRESS("В процессе"),
    COMPLETED("Завершён"),
    FAILED("Провален"),
    EXPIRED("Время истекло");

    private final String displayName;

    SimulationSessionStatus(String displayName) {
        this.displayName = displayName;
    }
}
