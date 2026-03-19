package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Раздел теории.
 * Содержит учебные материалы для подготовки специалистов по пневмоиспытаниям.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_theorySection")
@EqualsAndHashCode(of = "id")
public class TheorySection {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название раздела */
    @Column(nullable = false, length = 255)
    private String title;

    /** Порядок сортировки */
    @Column(nullable = false)
    private Integer sortOrder;

    /** Описание раздела */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Список материалов раздела */
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TheoryMaterial> materials = new ArrayList<>();
}
