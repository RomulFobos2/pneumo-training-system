package ru.mai.voshod.pneumotraining.repo;

import com.mai.siarsp.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    List<Role> findByNameStartingWith(String prefix);
}
