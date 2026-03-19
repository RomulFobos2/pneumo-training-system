package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Тип учебного материала
 */
@Getter
public enum MaterialType {

    TEXT("Текст"),
    PDF("PDF-документ"),
    VIDEO_LINK("Видео");

    private final String displayName;

    MaterialType(String displayName) {
        this.displayName = displayName;
    }
}
