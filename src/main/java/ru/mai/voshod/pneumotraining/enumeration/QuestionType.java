package ru.mai.voshod.pneumotraining.enumeration;

import lombok.Getter;

/**
 * Тип вопроса в тесте
 */
@Getter
public enum QuestionType {

    SINGLE_CHOICE("Один ответ"),
    MULTIPLE_CHOICE("Несколько ответов"),
    SEQUENCE("Очерёдность"),
    MATCHING("Соответствие"),
    OPEN_TEXT("Открытый ответ");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }
}
