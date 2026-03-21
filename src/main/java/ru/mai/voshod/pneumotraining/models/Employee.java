package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

/**
 * Сотрудник группы обеспечения пневмоиспытаний ДУ РКН
 *
 * Представляет пользователя системы АОС ПИ с определенной ролью и правами доступа.
 * Реализует интерфейс UserDetails из Spring Security для аутентификации и авторизации.
 *
 * Связи:
 * - Каждый сотрудник имеет одну роль (Role)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_employee")
public class Employee implements UserDetails {

    // ========== ПОЛЯ ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Фамилия сотрудника */
    @NotNull
    @Column(length = 50)
    private String lastName;

    /** Имя сотрудника */
    @NotNull
    @Column(length = 50)
    private String firstName;

    /** Отчество сотрудника */
    @Column(length = 50)
    private String middleName;

    /** Дата рождения */
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate birthDate;

    /** Подразделение */
    @ManyToOne
    @JoinColumn(name = "department_id")
    @ToString.Exclude
    private Department department;

    /** Должность */
    @Column(length = 200)
    private String position;

    /**
     * Логин для входа в систему
     * Уникальный идентификатор для аутентификации
     */
    @NotNull
    @Column(unique = true, length = 50)
    private String username;

    /**
     * Хеш пароля (BCrypt)
     */
    @NotNull
    @Column(length = 200)
    private String password;

    /**
     * Роль сотрудника в системе
     */
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    @ToString.Exclude
    private Role role;

    /**
     * Активность учетной записи
     * true — сотрудник активен, может входить в систему
     * false — деактивирован
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Требуется смена пароля при следующем входе
     * Устанавливается в true при создании пользователя и при сбросе пароля администратором
     */
    @Column(nullable = false)
    private boolean needChangePassword = false;

    // ========== КОНСТРУКТОРЫ ==========

    /**
     * Создает нового сотрудника с базовыми данными
     *
     * @param lastName фамилия
     * @param firstName имя
     * @param middleName отчество
     * @param username логин (уникальный)
     * @param password пароль (уже зашифрован BCrypt)
     */
    public Employee(String lastName, String firstName, String middleName, String username, String password) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.username = username;
        this.password = password;
    }

    // ========== МЕТОДЫ Spring Security UserDetails ==========

    /**
     * Возвращает права доступа сотрудника
     * @return коллекция с одной ролью
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(role);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Активна ли учетная запись
     * @return значение поля isActive
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }

    // ========== МЕТОДЫ ==========

    /**
     * Полное ФИО сотрудника: "Фамилия Имя Отчество"
     */
    @Transient
    public String getFullName() {
        return lastName + " " + firstName + (middleName != null ? " " + middleName : "");
    }

    @Override
    public String toString() {
        return "Employee{" +
                "username='" + username + '\'' +
                ", role=" + (role != null ? role.getName() : "null") +
                '}';
    }
}
