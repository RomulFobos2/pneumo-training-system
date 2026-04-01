package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/*
 * Хранение зависит от типа вопроса:
 * SINGLE_CHOICE / MULTIPLE_CHOICE / MATCHING — selectedAnswers (ManyToMany)
 * SEQUENCE — answerText с порядком ID через запятую
 * OPEN_TEXT — answerText с текстом ответа
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testSessionAnswer")
public class TestSessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // для OPEN_TEXT и SEQUENCE
    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(nullable = false)
    private boolean isCorrect = false;

    @Column
    private Double earnedScoreRatio;

    @ManyToOne
    @JoinColumn(name = "test_session_id", nullable = false)
    @ToString.Exclude
    private TestSession testSession;

    @ManyToOne
    @JoinColumn(name = "test_question_id", nullable = false)
    @ToString.Exclude
    private TestQuestion testQuestion;

    // для SINGLE/MULTIPLE/MATCHING
    @ManyToMany
    @JoinTable(name = "t_testSessionAnswer_selectedAnswers",
            joinColumns = @JoinColumn(name = "session_answer_id"),
            inverseJoinColumns = @JoinColumn(name = "test_answer_id"))
    @ToString.Exclude
    private List<TestAnswer> selectedAnswers = new ArrayList<>();
}
