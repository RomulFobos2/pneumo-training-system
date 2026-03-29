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

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testSession")
public class TestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    // startedAt + timeLimit; null = без ограничения
    private LocalDateTime endTime;

    private LocalDateTime finishedAt;

    private Integer score;

    private Integer totalScore;

    private Double scorePercent;

    private Boolean isPassed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestSessionStatus sessionStatus;

    // JSON-массив ID вопросов, например [5,2,8,1,3]
    @Column(columnDefinition = "TEXT")
    private String questionOrder;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    @OneToMany(mappedBy = "testSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TestSessionAnswer> answers = new ArrayList<>();
}
