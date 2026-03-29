package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.ElementType;

/**
 * Элемент мнемосхемы (клапан, насос, датчик и т.д.).
 *
 * Размещается на холсте схемы с координатами и начальным состоянием.
 *
 * Связи:
 * - Схема (MnemoSchema)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_schemaElement")
public class SchemaElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название элемента (например, "VP7", "N1", "PT3") */
    @NotNull
    @Column(nullable = false, length = 100)
    private String name;

    /** Тип элемента */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ElementType elementType;

    /** Координата X на холсте */
    @Column(nullable = false)
    private Double posX = 0.0;

    /** Координата Y на холсте */
    @Column(nullable = false)
    private Double posY = 0.0;

    /** Ширина элемента (px) */
    @Column(nullable = false)
    private Double width = 60.0;

    /** Высота элемента (px) */
    @Column(nullable = false)
    private Double height = 60.0;

    /** Начальное состояние (false = выкл/закрыт, true = вкл/открыт) */
    @Column(nullable = false)
    private boolean initialState = false;

    /** Угол поворота (градусы) */
    @Column(nullable = false)
    private Integer rotation = 0;

    /** Минимальное значение для датчика (null = по умолчанию) */
    @Column
    private Double minValue;

    /** Максимальное значение для датчика (null = по умолчанию) */
    @Column
    private Double maxValue;

    /** Схема, которой принадлежит элемент */
    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;
}
