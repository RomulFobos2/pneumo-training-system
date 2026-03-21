package ru.mai.voshod.pneumotraining.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.TestAssignmentEmployee;
import ru.mai.voshod.pneumotraining.repo.TestAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class NotificationScheduler {

    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final NotificationService notificationService;

    public NotificationScheduler(TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                                 NotificationService notificationService) {
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendDeadlineReminders() {
        log.info("Запуск отправки напоминаний о дедлайнах");
        LocalDate today = LocalDate.now();
        String testLink = "/employee/specialist/testing/availableTests";

        sendRemindersForDate(today.plusDays(5), "До дедлайна осталось 5 дней", testLink);
        sendRemindersForDate(today.plusDays(1), "До дедлайна остался 1 день", testLink);
        sendRemindersForDate(today, "Сегодня последний день сдачи теста", testLink);

        log.info("Отправка напоминаний завершена");
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void markOverdueAssignments() {
        log.info("Запуск проверки просроченных назначений");
        LocalDate today = LocalDate.now();
        String testLink = "/employee/specialist/testing/availableTests";

        List<TestAssignmentEmployee> overdue = testAssignmentEmployeeRepository
                .findByStatusAndAssignment_DeadlineBefore(AssignmentStatus.PENDING, today);

        for (TestAssignmentEmployee ae : overdue) {
            ae.setStatus(AssignmentStatus.OVERDUE);
            testAssignmentEmployeeRepository.save(ae);

            notificationService.createNotification(ae.getEmployee(),
                    "Срок сдачи теста «" + ae.getAssignment().getTest().getTitle() + "» истёк",
                    testLink);
        }

        log.info("Помечено просроченных назначений: {}", overdue.size());
    }

    private void sendRemindersForDate(LocalDate deadline, String messagePrefix, String link) {
        List<TestAssignmentEmployee> assignments = testAssignmentEmployeeRepository
                .findByStatusAndAssignment_Deadline(AssignmentStatus.PENDING, deadline);

        for (TestAssignmentEmployee ae : assignments) {
            String testTitle = ae.getAssignment().getTest().getTitle();
            notificationService.createNotification(ae.getEmployee(),
                    messagePrefix + ": тест «" + testTitle + "»",
                    link);
        }

        if (!assignments.isEmpty()) {
            log.info("Отправлено напоминаний для даты {}: {}", deadline, assignments.size());
        }
    }
}
