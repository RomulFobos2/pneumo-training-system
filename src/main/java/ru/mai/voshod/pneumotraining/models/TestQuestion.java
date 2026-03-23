package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Вопрос теста.
 *
 * Поддерживает 5 типов: один ответ, несколько ответов, очерёдность, соответствие, открытый ответ.
 *
 * Связи:
 * - Принадлежит тесту (Test)
 * - Имеет варианты ответа (TestAnswer)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testQuestion")
public class TestQuestion {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Текст вопроса */
    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** Порядок сортировки */
    @Column(nullable = false)
    private Integer sortOrder;

    /** Тип вопроса */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType questionType;

    /** Раздел теории, к которому относится вопрос (для адаптивных рекомендаций) */
    @ManyToOne
    @JoinColumn(name = "theory_section_id")
    @ToString.Exclude
    private TheorySection theorySection;

    /** Тест, к которому относится вопрос */
    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    /** Варианты ответа */
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @ToString.Exclude
    private List<TestAnswer> answers = new ArrayList<>();
}
