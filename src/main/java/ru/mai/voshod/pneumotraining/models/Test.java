package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_test")
public class Test {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // минуты, 0 = без ограничения
    @Column(nullable = false)
    private Integer timeLimit = 0;

    // процент (0–100)
    @Column(nullable = false)
    private Integer passingScore = 60;

    @Column(nullable = false)
    private boolean availableWithoutAssignment = false;

    @Column(nullable = false)
    private boolean allowBackNavigation = false;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    @ManyToMany
    @JoinTable(
            name = "t_test_department",
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @ToString.Exclude
    private List<Department> allowedDepartments = new ArrayList<>();

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @ToString.Exclude
    private List<TestQuestion> questions = new ArrayList<>();
}
