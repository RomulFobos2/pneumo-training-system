package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_theorySection")
@EqualsAndHashCode(of = "id")
public class TheorySection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TheoryMaterial> materials = new ArrayList<>();
}
