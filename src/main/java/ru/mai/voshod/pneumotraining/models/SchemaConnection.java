package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_schemaConnection")
public class SchemaConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "schema_id", nullable = false)
    @ToString.Exclude
    private MnemoSchema schema;

    @ManyToOne
    @JoinColumn(name = "source_element_id", nullable = false)
    @ToString.Exclude
    private SchemaElement sourceElement;

    @ManyToOne
    @JoinColumn(name = "target_element_id", nullable = false)
    @ToString.Exclude
    private SchemaElement targetElement;

    // SVG path data (атрибут d) или JSON с waypoints
    @Column(columnDefinition = "TEXT")
    private String pathData;
}
