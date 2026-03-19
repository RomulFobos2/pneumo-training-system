package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Статус тестовой сессии
 */
@Getter
public enum TestSessionStatus {

    IN_PROGRESS("В процессе"),
    COMPLETED("Завершён"),
    EXPIRED("Время истекло"),
    INTERRUPTED("Прерван");

    private final String displayName;

    TestSessionStatus(String displayName) {
        this.displayName = displayName;
    }
}
