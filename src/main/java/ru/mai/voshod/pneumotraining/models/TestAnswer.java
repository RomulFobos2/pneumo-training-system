package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Вариант ответа на вопрос теста.
 *
 * Для разных типов вопросов поля используются по-разному:
 * - SINGLE_CHOICE / MULTIPLE_CHOICE: answerText + isCorrect
 * - SEQUENCE: answerText + sortOrder (порядок определяет правильную последовательность)
 * - MATCHING: answerText (левый столбец) + matchTarget (правый столбец)
 * - OPEN_TEXT: answerText содержит эталонный ответ
 *
 * Связи:
 * - Принадлежит вопросу (TestQuestion)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testAnswer")
public class TestAnswer {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Текст варианта ответа */
    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerText;

    /** Является ли ответ правильным (для SINGLE_CHOICE / MULTIPLE_CHOICE) */
    @Column(nullable = false)
    private boolean isCorrect = false;

    /** Порядок сортировки / позиция в последовательности (для SEQUENCE) */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    /** Цель соответствия (для MATCHING: правый столбец) */
    @Column(length = 500)
    private String matchTarget;

    /** Вопрос, к которому относится ответ */
    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    private TestQuestion question;
}
