package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

@Getter
public enum AssignmentStatus {
    PENDING("Ожидает"),
    COMPLETED("Выполнено"),
    OVERDUE("Просрочено");

    private final String displayName;

    AssignmentStatus(String displayName) {
        this.displayName = displayName;
    }
}
