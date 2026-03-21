package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testAssignment")
public class TestAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_id", nullable = false)
    @ToString.Exclude
    private Test test;

    @NotNull
    @Column(nullable = false)
    private LocalDate deadline;

    @ManyToOne
    @JoinColumn(name = "created_by_id", nullable = false)
    @ToString.Exclude
    private Employee createdBy;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TestAssignmentEmployee> assignedEmployees = new ArrayList<>();

    public TestAssignment(Test test, LocalDate deadline, Employee createdBy) {
        this.test = test;
        this.deadline = deadline;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }
}
