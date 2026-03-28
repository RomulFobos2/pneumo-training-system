package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

@Getter
public enum ScenarioType {
    NORMAL("Штатный"),
    FAULT("Аварийный");

    private final String displayName;

    ScenarioType(String displayName) {
        this.displayName = displayName;
    }
}
