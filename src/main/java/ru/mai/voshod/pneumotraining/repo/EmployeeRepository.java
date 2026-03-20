package ru.mai.voshod.pneumotraining.repo;

import ru.mai.voshod.pneumotraining.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    List<Employee> findAllByOrderByLastNameAsc();

    List<Employee> findAllByRoleName(String roleName);

    long countByIsActiveTrue();
}
