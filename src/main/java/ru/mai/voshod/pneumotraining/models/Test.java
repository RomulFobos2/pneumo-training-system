package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Тест для проверки знаний специалистов.
 *
 * Содержит вопросы разных типов и параметры прохождения (таймер, проходной балл).
 * Создаётся начальником группы (Chief).
 *
 * Связи:
 * - Создатель теста (Employee)
 * - Список вопросов (TestQuestion)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_test")
public class Test {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название теста */
    @NotNull
    @Column(nullable = false, length = 255)
    private String title;

    /** Описание теста */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Ограничение по времени (в минутах), 0 = без ограничения */
    @Column(nullable = false)
    private Integer timeLimit = 0;

    /** Проходной балл (в процентах, 0–100) */
    @Column(nullable = false)
    private Integer passingScore = 60;

    /** Является ли тест экзаменом (отображается в протоколах) */
    @Column(nullable = false)
    private boolean isExam = false;

    /** Доступен ли тест без назначения (для свободного прохождения) */
    @Column(nullable = false)
    private boolean availableWithoutAssignment = false;

    /** Разрешена ли навигация назад при прохождении теста */
    @Column(nullable = false)
    private boolean allowBackNavigation = false;

    /** Создатель теста */
    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    /** Подразделения, которым тест доступен без назначения */
    @ManyToMany
    @JoinTable(
            name = "t_test_department",
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @ToString.Exclude
    private List<Department> allowedDepartments = new ArrayList<>();

    /** Вопросы теста */
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @ToString.Exclude
    private List<TestQuestion> questions = new ArrayList<>();
}
