package ru.mai.voshod.pneumotraining.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Role;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.RoleRepository;

/**
 * Инициализация начальных данных при запуске приложения.
 * Создаёт 4 роли и учётную запись администратора по умолчанию.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           EmployeeRepository employeeRepository,
                           BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createRoleIfNotExists("ROLE_EMPLOYEE_ADMIN", "Администратор");
        createRoleIfNotExists("ROLE_EMPLOYEE_CHIEF", "Начальник группы");
        createRoleIfNotExists("ROLE_EMPLOYEE_SPECIALIST", "Специалист");
        createRoleIfNotExists("ROLE_EMPLOYEE_OPERATOR", "Оператор");

        if (employeeRepository.findByUsername("admin").isEmpty()) {
            Role adminRole = roleRepository.findByName("ROLE_EMPLOYEE_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Роль ROLE_EMPLOYEE_ADMIN не найдена"));

            Employee admin = new Employee("Администратор", "Системный", "Пользователь", "admin",
                    bCryptPasswordEncoder.encode("admin"));
            admin.setRole(adminRole);
            admin.setActive(true);
            admin.setNeedChangePassword(true);
            admin.setSubdivision("Администрация");
            admin.setPosition("Системный администратор");
            employeeRepository.save(admin);
            log.info("Создан администратор по умолчанию: admin/admin");
        }
    }

    private void createRoleIfNotExists(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description));
            log.info("Создана роль: {} ({})", name, description);
        }
    }
}
