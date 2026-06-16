package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

@Getter
public enum ProtocolType {

    TEST("Тест"),
    SIM("Мнемосхема");

    private final String displayName;

    ProtocolType(String displayName) {
        this.displayName = displayName;
    }
}
