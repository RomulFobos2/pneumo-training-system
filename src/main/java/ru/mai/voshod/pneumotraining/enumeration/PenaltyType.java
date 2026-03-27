package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Тип штрафа за запрещённое действие в симуляции.
 */
@Getter
public enum PenaltyType {

    FAIL("Провал"),
    WARNING("Предупреждение"),
    TIME_PENALTY("Штраф времени");

    private final String displayName;

    PenaltyType(String displayName) {
        this.displayName = displayName;
    }
}
