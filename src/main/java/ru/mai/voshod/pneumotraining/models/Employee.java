package ru.mai.voshod.pneumotraining.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

/**
 * Сотрудник ИП "Левчук"
 *
 * Представляет пользователя системы СИАРСП с определенной ролью и правами доступа.
 * Реализует интерфейс UserDetails из Spring Security для аутентификации и авторизации.
 *
 * Согласно ТЗ, в штатной структуре ИП "Левчук" есть следующие должности:
 * - Директор (администратор системы) - полный доступ ко всем функциям
 * - Бухгалтер - доступ к финансовым документам
 * - Заведующий складом - управление товарами и складом
 * - Водитель-экспедитор - доступ к путевым документам через мобильное приложение
 *
 * Связи:
 * - Каждый сотрудник имеет одну роль (Role)
 * - Может быть ответственным за заказы клиентов (ClientOrder.responsibleEmployee)
 * - Может быть водителем в задачах доставки (DeliveryTask.driver)
 * - Может оформлять приемо-сдаточные акты (AcceptanceAct.deliveredBy)
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_employee")
public class Employee implements UserDetails {

    // ========== ПОЛЯ ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Фамилия сотрудника
     * Используется для формирования ФИО в документах и интерфейсе
     */
    @NotNull
    @Column(length = 50)
    private String lastName;

    /**
     * Имя сотрудника
     */
    @NotNull
    @Column(length = 50)
    private String firstName;

    /**
     * Отчество сотрудника
     */
    @NotNull
    @Column(length = 50)
    private String patronymicName;

    /**
     * Логин для входа в систему
     * Уникальный идентификатор для аутентификации
     * Используется при входе в web-приложение или мобильное приложение
     */
    @NotNull
    @Column(unique = true, length = 50)
    private String username;

    /**
     * Хеш пароля
     * Хранится в зашифрованном виде (BCrypt)
     * Никогда не хранится в открытом виде
     */
    @NotNull
    @Column(length = 200)
    private String password;

    /**
     * Требуется ли смена пароля при следующем входе
     * true - сотрудник должен сменить пароль (обычно при первом входе или после сброса)
     * false - пароль был изменен, смена не требуется
     */
    @Column(nullable = false)
    private boolean needChangePass = true;

    /**
     * Активность учетной записи
     * true - сотрудник активен, может входить в систему
     * false - сотрудник уволен или временно заблокирован
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Дата регистрации сотрудника в системе
     * Автоматически устанавливается при создании учетной записи
     * Используется для учета и отчетности
     */
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate dateOfRegistration;

    /**
     * Роль сотрудника в системе
     */
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Специализация сотрудника
     * Например: "Логистика", "Бухгалтерский учёт", "Складская логистика"
     * Пустая строка означает, что специализация не указана
     */
    @Column(length = 200, nullable = false)
    private String specialization = "";

    /**
     * Квалификация сотрудника
     * Например: "Высшая категория", "1-й разряд"
     * Пустая строка означает, что квалификация не указана
     */
    @Column(length = 200, nullable = false)
    private String qualification = "";

    /**
     * Заработная плата сотрудника (руб.)
     */
    @Column
    private BigDecimal salary;

    /**
     * Имя файла приказа о приёме на работу
     * Хранится в каталоге, указанном в свойстве contract.upload.path
     */
    @Column(length = 500)
    private String hiringOrderFile;

    /**
     * Имя файла приказа об увольнении
     * Хранится в каталоге, указанном в свойстве contract.upload.path
     */
    @Column(length = 500)
    private String dismissalOrderFile;

    // ========== КОНСТРУКТОРЫ ==========

    /**
     * Создает нового сотрудника с базовыми данными
     * Автоматически устанавливает дату регистрации на текущую
     *
     * @param lastName фамилия сотрудника
     * @param firstName имя сотрудника
     * @param patronymicName отчество сотрудника
     * @param username логин для входа в систему (должен быть уникальным)
     * @param password пароль (будет зашифрован перед сохранением)
     */
    public Employee(String lastName, String firstName, String patronymicName, String username, String password) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.patronymicName = patronymicName;
        this.username = username;
        this.password = password;
        this.dateOfRegistration = LocalDate.now();
        this.specialization = "";
        this.qualification = "";
    }

    // ========== МЕТОДЫ Spring Security UserDetails ==========

    /**
     * Возвращает права доступа (authorities) сотрудника
     * Используется Spring Security для проверки прав доступа к ресурсам
     *
     * @return коллекция с одной ролью сотрудника
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role.getName()));
    }

    /**
     * Проверяет, не истек ли срок действия учетной записи
     * В текущей реализации учетные записи не имеют срока действия
     *
     * @return true - учетная запись не истекла
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Проверяет, не заблокирована ли учетная запись
     * В текущей реализации блокировка осуществляется через поле isActive
     *
     * @return true - учетная запись не заблокирована
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Проверяет, не истек ли срок действия пароля
     * В текущей реализации пароли не имеют срока действия
     *
     * @return true - пароль не истек
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Проверяет, активна ли учетная запись
     * Используется Spring Security для контроля доступа
     *
     * @return значение поля isActive
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }

    // ========== МЕТОДЫ ==========

    /**
     * Возвращает полное ФИО сотрудника
     * Формат: "Фамилия Имя Отчество"
     * Используется в интерфейсе и документах
     *
     * @return полное ФИО в формате "Иванов Иван Иванович"
     */
    @Transient
    public String getFullName() {
        return lastName + " " + firstName + " " + patronymicName;
    }

    /**
     * Строковое представление сотрудника для логов
     * Показывает логин и роль, не раскрывая чувствительных данных
     *
     * @return строка вида "Employee{username='admin', role=ROLE_ADMIN}"
     */
    @Override
    public String toString() {
        return "Employee{" +
                "username='" + username + '\'' +
                ", role=" + role.getName() +
                '}';
    }
}