package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.mai.voshod.pneumotraining.enumeration.ElementType;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_schemaElement")
public class SchemaElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // например "VP7", "PT3"
    @NotNull
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ElementType elementType;

    @Column(nullable = false)
    private Double posX = 0.0;

    @Column(nullable = false)
    private Double posY = 0.0;

    @Column(nullable = false)
    private Double width = 60.0;

    @Column(nullable = false)
    private Double height = 60.0;

    // false = выкл/закрыт, true = вкл/открыт
    @Column(nullable = false)
    private boolean initialState = false;

    // градусы
    @Column(nullable = false)
    private Integer rotation = 0;

    // диапазон для датчиков; null = по умолчанию
    @Column
    private Double minValue;

    @Column
    private Double maxValue;

    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;
}
