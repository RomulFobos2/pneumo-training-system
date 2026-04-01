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

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testQuestion")
public class TestQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false)
    private Integer difficultyLevel = 1;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType questionType;

    // для адаптивных рекомендаций
    @ManyToOne
    @JoinColumn(name = "theory_section_id")
    @ToString.Exclude
    private TheorySection theorySection;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @ToString.Exclude
    private List<TestAnswer> answers = new ArrayList<>();
}
