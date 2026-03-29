package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/*
 * Использование полей зависит от типа вопроса:
 * SINGLE_CHOICE / MULTIPLE_CHOICE: answerText + isCorrect
 * SEQUENCE: answerText + sortOrder (порядок = правильная последовательность)
 * MATCHING: answerText (левый столбец) + matchTarget (правый столбец)
 * OPEN_TEXT: answerText = эталонный ответ
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testAnswer")
public class TestAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerText;

    // для SINGLE_CHOICE / MULTIPLE_CHOICE
    @Column(nullable = false)
    private boolean isCorrect = false;

    // позиция в последовательности (SEQUENCE)
    @Column(nullable = false)
    private Integer sortOrder = 0;

    // правый столбец (MATCHING)
    @Column(length = 500)
    private String matchTarget;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    private TestQuestion question;
}
