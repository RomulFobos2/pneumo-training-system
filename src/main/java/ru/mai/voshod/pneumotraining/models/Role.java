package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

/**
 * Роль сотрудника в системе АОС ПИ
 *
 * Определяет права доступа и функциональные возможности пользователя.
 * Реализует интерфейс GrantedAuthority из Spring Security.
 *
 * В системе предусмотрено 4 роли:
 * 1. ROLE_EMPLOYEE_ADMIN (Администратор) — управление пользователями
 * 2. ROLE_EMPLOYEE_CHIEF (Начальник группы) — тесты, материалы, протоколы, аналитика
 * 3. ROLE_EMPLOYEE_SPECIALIST (Специалист) — теория, тестирование, результаты
 * 4. ROLE_EMPLOYEE_OPERATOR (Оператор) — те же права что у специалиста
 *
 * Связи:
 * - Одна роль может быть назначена множеству сотрудников (Employee)
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_role")
public class Role implements GrantedAuthority {

    // ========== ПОЛЯ ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Техническое название роли (например "ROLE_EMPLOYEE_ADMIN")
     * Используется в Spring Security для проверки прав доступа
     */
    @NotNull
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Человекочитаемое описание роли (например "Администратор")
     * Отображается в интерфейсе
     */
    @NotNull
    @Column(nullable = false)
    private String description;

    // ========== КОНСТРУКТОРЫ ==========

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ========== МЕТОДЫ Spring Security ==========

    /**
     * Возвращает название роли для Spring Security
     *
     * @return техническое название роли (например "ROLE_EMPLOYEE_ADMIN")
     */
    @Override
    public String getAuthority() {
        return getName();
    }
}
