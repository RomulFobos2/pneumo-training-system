package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    private Employee employee;

    @NotNull
    @Column(length = 500)
    private String message;

    @Column(length = 500)
    private String link;

    @Column(nullable = false)
    private boolean isRead = false;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Notification(Employee employee, String message, String link) {
        this.employee = employee;
        this.message = message;
        this.link = link;
        this.createdAt = LocalDateTime.now();
    }
}
