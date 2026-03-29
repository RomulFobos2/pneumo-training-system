package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_theoryMaterial")
@EqualsAndHashCode(of = "id")
public class TheoryMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    // HTML-текст, путь к PDF или URL видео
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialType materialType;

    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    @ToString.Exclude
    private TheorySection section;
}
