package ru.mai.voshod.pneumotraining.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.voshod.pneumotraining.models.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    long countByEmployeeIdAndIsReadFalse(Long employeeId);

    List<Notification> findByEmployeeIdAndIsReadFalse(Long employeeId);
}
