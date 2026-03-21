package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Соединение (трубопровод) между двумя элементами мнемосхемы.
 *
 * Хранит SVG path data для отрисовки линии соединения.
 *
 * Связи:
 * - Схема (MnemoSchema)
 * - Элемент-источник (SchemaElement)
 * - Элемент-цель (SchemaElement)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_schemaConnection")
public class SchemaConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Схема, которой принадлежит соединение */
    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;

    /** Элемент-источник */
    @ManyToOne
    @JoinColumn(name = "source_element_id", nullable = false)
    @ToString.Exclude
    private SchemaElement sourceElement;

    /** Элемент-цель */
    @ManyToOne
    @JoinColumn(name = "target_element_id", nullable = false)
    @ToString.Exclude
    private SchemaElement targetElement;

    /** SVG path data (атрибут d) или JSON с waypoints для отрисовки трубопровода */
    @Column(columnDefinition = "TEXT")
    private String pathData;
}
