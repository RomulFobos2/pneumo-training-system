package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сессия прохождения теста.
 *
 * Создаётся при старте теста специалистом.
 * Хранит порядок вопросов (JSON), ответы, результат.
 *
 * Связи:
 * - Сотрудник (Employee)
 * - Тест (Test)
 * - Ответы сессии (TestSessionAnswer)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testSession")
public class TestSession {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Время начала прохождения */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Время окончания (вычисляется: startedAt + timeLimit). Null = без ограничения */
    private LocalDateTime endTime;

    /** Фактическое время завершения */
    private LocalDateTime finishedAt;

    /** Набранные баллы */
    private Integer score;

    /** Максимально возможные баллы */
    private Integer totalScore;

    /** Процент правильных ответов */
    private Double scorePercent;

    /** Пройден ли тест */
    private Boolean isPassed;

    /** Статус сессии */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestSessionStatus sessionStatus;

    /** Порядок вопросов (JSON-массив ID, например [5,2,8,1,3]) */
    @Column(columnDefinition = "TEXT")
    private String questionOrder;

    /** Сотрудник, проходящий тест */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    /** Тест */
    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    /** Ответы сессии */
    @OneToMany(mappedBy = "testSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TestSessionAnswer> answers = new ArrayList<>();
}
