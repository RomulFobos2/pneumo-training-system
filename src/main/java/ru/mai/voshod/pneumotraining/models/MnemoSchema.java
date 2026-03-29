package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_mnemoSchema")
public class MnemoSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 255, unique = true)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // px
    @Column(nullable = false)
    private Integer width = 1200;

    @Column(nullable = false)
    private Integer height = 800;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private Employee createdBy;

    @OneToMany(mappedBy = "schema", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SchemaElement> elements = new ArrayList<>();

    @OneToMany(mappedBy = "schema", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SchemaConnection> connections = new ArrayList<>();
}
