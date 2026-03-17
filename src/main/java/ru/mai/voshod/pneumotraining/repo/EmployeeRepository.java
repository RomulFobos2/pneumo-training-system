package ru.mai.voshod.pneumotraining.repo;

import com.mai.siarsp.models.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    List<Employee> findByRoleName(String roleName);

    List<Employee> findAllByRoleName(String roleName);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("SELECT e FROM Employee e WHERE " +
            "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.patronymicName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(e.dateOfRegistration AS string) LIKE CONCAT('%', :searchTerm, '%')")
    Page<Employee> searchEmployeesByMultipleFields(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))")
    Page<Employee> findByLastName(@Param("lastName") String lastName, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))")
    Page<Employee> findByFirstName(@Param("firstName") String firstName, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.patronymicName) LIKE LOWER(CONCAT('%', :patronymicName, '%'))")
    Page<Employee> findByPatronymicName(@Param("patronymicName") String patronymicName, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    Page<Employee> findByUsernameContaining(@Param("username") String username, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.dateOfRegistration BETWEEN :startDate AND :endDate")
    Page<Employee> findByDateOfRegistrationBetween(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate,
                                                   Pageable pageable);

}
