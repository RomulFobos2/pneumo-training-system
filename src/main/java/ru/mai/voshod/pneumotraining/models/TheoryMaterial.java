package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;

/**
 * Учебный материал.
 * Содержит текстовый контент, ссылку на PDF или ссылку на видео.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_theoryMaterial")
@EqualsAndHashCode(of = "id")
public class TheoryMaterial {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название материала */
    @Column(nullable = false, length = 255)
    private String title;

    /** Содержимое: HTML-текст, URL PDF-документа или URL видео */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Порядок сортировки */
    @Column(nullable = false)
    private Integer sortOrder;

    /** Тип материала */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialType materialType;

    /** Раздел, к которому относится материал */
    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    @ToString.Exclude
    private TheorySection section;
}
