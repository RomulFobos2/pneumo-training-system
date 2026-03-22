package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_testAssignmentEmployee",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "employee_id"}))
public class TestAssignmentEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "assignment_id", nullable = false)
    @ToString.Exclude
    private TestAssignment assignment;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AssignmentStatus status = AssignmentStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "completed_session_id")
    @ToString.Exclude
    private TestSession completedSession;

    @ManyToOne
    @JoinColumn(name = "completed_simulation_session_id")
    @ToString.Exclude
    private SimulationSession completedSimulationSession;

    public TestAssignmentEmployee(TestAssignment assignment, Employee employee) {
        this.assignment = assignment;
        this.employee = employee;
        this.status = AssignmentStatus.PENDING;
    }
}
