package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.voshod.pneumotraining.models.Test;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    List<Test> findAllByOrderByIdDesc();

    List<Test> findAllByOrderByTitleAsc();

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);

    /** Тесты, доступные без назначения */
    List<Test> findByAvailableWithoutAssignmentTrueOrderByTitleAsc();

    /** Тесты, доступные без назначения для конкретного подразделения */
    @Query("SELECT t FROM Test t JOIN t.allowedDepartments d WHERE t.availableWithoutAssignment = true AND d.id = :departmentId ORDER BY t.title ASC")
    List<Test> findAvailableByDepartmentId(@Param("departmentId") Long departmentId);

    /** Тесты, доступные без назначения для списка подразделений (с наследованием вверх по дереву) */
    @Query("SELECT DISTINCT t FROM Test t JOIN t.allowedDepartments d WHERE t.availableWithoutAssignment = true AND d.id IN :departmentIds ORDER BY t.title ASC")
    List<Test> findAvailableByDepartmentIds(@Param("departmentIds") List<Long> departmentIds);

    /** Тесты, привязанные к конкретному подразделению (без учёта availableWithoutAssignment) */
    @Query("SELECT DISTINCT t FROM Test t JOIN t.allowedDepartments d WHERE d.id = :departmentId ORDER BY t.title ASC")
    List<Test> findByDepartmentId(@Param("departmentId") Long departmentId);

    /** Тесты, привязанные к списку подразделений (с наследованием вверх по дереву, без учёта availableWithoutAssignment) */
    @Query("SELECT DISTINCT t FROM Test t JOIN t.allowedDepartments d WHERE d.id IN :departmentIds ORDER BY t.title ASC")
    List<Test> findByDepartmentIds(@Param("departmentIds") List<Long> departmentIds);
}
