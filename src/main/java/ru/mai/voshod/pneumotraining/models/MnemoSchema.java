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
 * Мнемосхема пневмосистемы.
 *
 * Контейнер для визуальной схемы с элементами и соединениями.
 * Создаётся начальником группы (Chief) через визуальный редактор.
 *
 * Связи:
 * - Создатель (Employee)
 * - Элементы схемы (SchemaElement)
 * - Соединения/трубопроводы (SchemaConnection)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_mnemoSchema")
public class MnemoSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название схемы */
    @NotNull
    @Column(nullable = false, length = 255, unique = true)
    private String title;

    /** Описание схемы */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Ширина холста (px) */
    @Column(nullable = false)
    private Integer width = 1200;

    /** Высота холста (px) */
    @Column(nullable = false)
    private Integer height = 800;

    /** Создатель схемы */
    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    /** Элементы на схеме */
    @OneToMany(mappedBy = "schema", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SchemaElement> elements = new ArrayList<>();

    /** Соединения (трубопроводы) между элементами */
    @OneToMany(mappedBy = "schema", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SchemaConnection> connections = new ArrayList<>();
}
