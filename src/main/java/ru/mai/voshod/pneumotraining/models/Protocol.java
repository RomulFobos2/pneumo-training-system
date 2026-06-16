package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.mai.voshod.pneumotraining.enumeration.ProtocolType;

import java.time.LocalDateTime;

/*
 * Сквозная нумерация протоколов проверки знаний (Word-документы).
 * id = «номер протокола» в шапке документа.
 * Уникальный индекс (type, session_id) — один протокол на одну сессию,
 * чтобы повторное скачивание возвращало тот же номер.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_protocol",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_protocol_type_session",
                columnNames = {"type", "session_id"}))
public class Protocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProtocolType type;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Protocol(ProtocolType type, Long sessionId) {
        this.type = type;
        this.sessionId = sessionId;
        this.createdAt = LocalDateTime.now();
    }
}
