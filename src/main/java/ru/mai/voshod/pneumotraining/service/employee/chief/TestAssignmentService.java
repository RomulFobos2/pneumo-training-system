package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.AssignedTestDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.mapper.TestAssignmentMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Test;
import ru.mai.voshod.pneumotraining.models.TestAssignment;
import ru.mai.voshod.pneumotraining.models.TestAssignmentEmployee;
import ru.mai.voshod.pneumotraining.models.TestSession;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.TestAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.TestAssignmentRepository;
import ru.mai.voshod.pneumotraining.repo.TestQuestionRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class TestAssignmentService {

    public enum DeleteAssignmentResult {
        DELETED,
        HAS_ATTEMPTS,
        NOT_FOUND,
        ERROR
    }

    private final TestAssignmentRepository testAssignmentRepository;
    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final TestRepository testRepository;
    private final EmployeeRepository employeeRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final NotificationService notificationService;

    public TestAssignmentService(TestAssignmentRepository testAssignmentRepository,
                                 TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                                 TestRepository testRepository,
                                 EmployeeRepository employeeRepository,
                                 TestQuestionRepository testQuestionRepository,
                                 NotificationService notificationService) {
        this.testAssignmentRepository = testAssignmentRepository;
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.testRepository = testRepository;
        this.employeeRepository = employeeRepository;
        this.testQuestionRepository = testQuestionRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<TestAssignmentDTO> getAllAssignments(String q,
                                                     String status,
                                                     Boolean hideCompleted,
                                                     LocalDate deadlineFrom,
                                                     LocalDate deadlineTo,
                                                     LocalDate createdFrom,
                                                     LocalDate createdTo) {
        return testAssignmentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAssignmentDTO)
                .filter(dto -> matchesAssignmentFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestAssignmentDTO> getAssignmentById(Long id) {
        return testAssignmentRepository.findById(id).map(this::toAssignmentDTO);
    }

    @Transactional(readOnly = true)
    public List<TestAssignmentEmployeeDTO> getAssignmentEmployees(Long assignmentId,
                                                                  String q,
                                                                  String status,
                                                                  Boolean hideCompleted,
                                                                  LocalDate deadlineFrom,
                                                                  LocalDate deadlineTo,
                                                                  LocalDate createdFrom,
                                                                  LocalDate createdTo) {
        return testAssignmentEmployeeRepository.findByAssignmentId(assignmentId)
                .stream()
                .map(TestAssignmentMapper.INSTANCE::toEmployeeDTO)
                .filter(dto -> matchesEmployeeFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional
    public Optional<Long> createAssignment(Long testId, LocalDate deadline, List<Long> employeeIds, Employee createdBy) {
        log.info("Создание назначения: testId={}, deadline={}, employees={}", testId, deadline, employeeIds.size());

        Optional<Test> testOptional = testRepository.findById(testId);
        if (testOptional.isEmpty()) {
            log.error("Тест не найден: id={}", testId);
            return Optional.empty();
        }

        Test test = testOptional.get();
        TestAssignment assignment = new TestAssignment(test, deadline, createdBy);
        testAssignmentRepository.save(assignment);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String testLink = "/employee/specialist/testing/availableTests";

        for (Long empId : employeeIds) {
            employeeRepository.findById(empId).ifPresent(employee -> {
                TestAssignmentEmployee ae = new TestAssignmentEmployee(assignment, employee);
                testAssignmentEmployeeRepository.save(ae);

                notificationService.createNotification(employee,
                        "Вам назначен тест «" + test.getTitle() + "». Срок сдачи: " + deadline.format(fmt),
                        testLink);
            });
        }

        log.info("Назначение создано: id={}, testId={}, employees={}", assignment.getId(), testId, employeeIds.size());
        return Optional.of(assignment.getId());
    }

    @Transactional
    public boolean deleteAssignment(Long id) {
        if (!testAssignmentRepository.existsById(id)) {
            return false;
        }
        testAssignmentRepository.deleteById(id);
        log.info("Назначение удалено: id={}", id);
        return true;
    }

    @Transactional
    public DeleteAssignmentResult deleteAssignmentWithStatus(Long id) {
        Optional<TestAssignment> assignmentOptional = testAssignmentRepository.findById(id);
        if (assignmentOptional.isEmpty()) {
            log.error("Назначение теста не найдено: id={}", id);
            return DeleteAssignmentResult.NOT_FOUND;
        }

        if (testAssignmentEmployeeRepository.existsAttemptForAssignment(id, AssignmentStatus.PENDING)) {
            log.error("Невозможно удалить назначение теста id={}: по нему уже есть прохождения.", id);
            return DeleteAssignmentResult.HAS_ATTEMPTS;
        }

        try {
            testAssignmentRepository.delete(assignmentOptional.get());
        } catch (Exception e) {
            log.error("Ошибка при удалении назначения теста id={}: {}", id, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return DeleteAssignmentResult.ERROR;
        }

        log.info("Назначение теста удалено: id={}", id);
        return DeleteAssignmentResult.DELETED;
    }

    @Transactional(readOnly = true)
    public List<AssignedTestDTO> getAssignmentsForEmployee(Long employeeId,
                                                           String q,
                                                           String status,
                                                           Boolean hideCompleted,
                                                           LocalDate deadlineFrom,
                                                           LocalDate deadlineTo,
                                                           LocalDate createdFrom,
                                                           LocalDate createdTo) {
        return testAssignmentEmployeeRepository.findByEmployeeIdOrderByAssignment_CreatedAtDesc(employeeId)
                .stream()
                .map(this::toAssignedTestDTO)
                .filter(dto -> matchesAssignedTestFilter(dto, q, status, hideCompleted, deadlineFrom, deadlineTo, createdFrom, createdTo))
                .toList();
    }

    @Transactional
    public void markAssignmentCompleted(Long employeeId, Long testId, TestSession session) {
        List<TestAssignmentEmployee> pending = testAssignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_TestIdAndStatus(employeeId, testId, AssignmentStatus.PENDING);

        for (TestAssignmentEmployee ae : pending) {
            ae.setStatus(AssignmentStatus.COMPLETED);
            ae.setCompletedSession(session);
            testAssignmentEmployeeRepository.save(ae);
            log.info("Назначение выполнено: assignmentEmployeeId={}, testId={}, employeeId={}",
                    ae.getId(), testId, employeeId);
        }
    }

    @Transactional
    public void markAssignmentFailed(Long employeeId, Long testId, TestSession session) {
        List<TestAssignmentEmployee> pending = testAssignmentEmployeeRepository
                .findByEmployeeIdAndAssignment_TestIdAndStatus(employeeId, testId, AssignmentStatus.PENDING);

        for (TestAssignmentEmployee ae : pending) {
            ae.setStatus(AssignmentStatus.FAILED);
            ae.setCompletedSession(session);
            testAssignmentEmployeeRepository.save(ae);
            log.info("Назначение не сдано: assignmentEmployeeId={}, testId={}, employeeId={}",
                    ae.getId(), testId, employeeId);
        }
    }

    private TestAssignmentDTO toAssignmentDTO(TestAssignment assignment) {
        TestAssignmentDTO dto = TestAssignmentMapper.INSTANCE.toDTO(assignment);
        List<TestAssignmentEmployee> employees = assignment.getAssignedEmployees();
        int completedCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED)
                .count();
        int overdueCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE)
                .count();
        int failedCount = (int) employees.stream()
                .filter(e -> e.getStatus() == AssignmentStatus.FAILED)
                .count();

        dto.setTotalAssigned(employees.size());
        dto.setCompletedCount(completedCount);
        dto.setOverdueCount(overdueCount);
        dto.setFailedCount(failedCount);
        dto.setFullyCompleted(!employees.isEmpty() && completedCount == employees.size());
        return dto;
    }

    private AssignedTestDTO toAssignedTestDTO(TestAssignmentEmployee ae) {
        Test test = ae.getAssignment().getTest();
        LocalDate today = LocalDate.now();

        AssignedTestDTO dto = new AssignedTestDTO();
        dto.setAssignmentEmployeeId(ae.getId());
        dto.setTestId(test.getId());
        dto.setTestTitle(test.getTitle());
        dto.setTestDescription(test.getDescription());
        dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
        dto.setTimeLimit(test.getTimeLimit() != null ? test.getTimeLimit() : 0);
        dto.setPassingScore(test.getPassingScore() != null ? test.getPassingScore() : 60);
        dto.setExam(test.isExam());
        dto.setDeadline(ae.getAssignment().getDeadline());
        dto.setCreatedAt(ae.getAssignment().getCreatedAt());
        dto.setStatusName(ae.getStatus().name());
        dto.setStatusDisplayName(ae.getStatus().getDisplayName());
        dto.setDaysUntilDeadline(ChronoUnit.DAYS.between(today, ae.getAssignment().getDeadline()));
        dto.setCompletedSessionId(ae.getCompletedSession() != null ? ae.getCompletedSession().getId() : null);
        return dto;
    }

    private boolean matchesAssignmentFilter(TestAssignmentDTO dto,
                                            String q,
                                            String status,
                                            Boolean hideCompleted,
                                            LocalDate deadlineFrom,
                                            LocalDate deadlineTo,
                                            LocalDate createdFrom,
                                            LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && dto.isFullyCompleted()) {
            return false;
        }
        if (!matchesAssignmentStatus(dto, status)) {
            return false;
        }
        if (!matchesText(q, dto.getAssignmentTitle(), dto.getCreatedByFullName(), dto.getTestTitle())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private boolean matchesEmployeeFilter(TestAssignmentEmployeeDTO dto,
                                          String q,
                                          String status,
                                          Boolean hideCompleted,
                                          LocalDate deadlineFrom,
                                          LocalDate deadlineTo,
                                          LocalDate createdFrom,
                                          LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && "COMPLETED".equals(dto.getStatusName())) {
            return false;
        }
        if (!matchesStatusName(dto.getStatusName(), status)) {
            return false;
        }
        if (!matchesText(q, dto.getEmployeeFullName())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private boolean matchesAssignedTestFilter(AssignedTestDTO dto,
                                              String q,
                                              String status,
                                              Boolean hideCompleted,
                                              LocalDate deadlineFrom,
                                              LocalDate deadlineTo,
                                              LocalDate createdFrom,
                                              LocalDate createdTo) {
        if (Boolean.TRUE.equals(defaultHideCompleted(hideCompleted)) && "COMPLETED".equals(dto.getStatusName())) {
            return false;
        }
        if (!matchesStatusName(dto.getStatusName(), status)) {
            return false;
        }
        if (!matchesText(q, dto.getTestTitle(), dto.getTestDescription())) {
            return false;
        }
        if (!matchesDateRange(dto.getDeadline(), deadlineFrom, deadlineTo)) {
            return false;
        }
        return matchesDateTimeRange(dto.getCreatedAt(), createdFrom, createdTo);
    }

    private Boolean defaultHideCompleted(Boolean hideCompleted) {
        return hideCompleted == null ? Boolean.TRUE : hideCompleted;
    }

    private boolean matchesAssignmentStatus(TestAssignmentDTO dto, String status) {
        AssignmentStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return true;
        }

        return switch (parsedStatus) {
            case PENDING -> dto.getTotalAssigned() - dto.getCompletedCount() - dto.getOverdueCount() - dto.getFailedCount() > 0;
            case COMPLETED -> dto.getCompletedCount() > 0;
            case FAILED -> dto.getFailedCount() > 0;
            case OVERDUE -> dto.getOverdueCount() > 0;
        };
    }

    private boolean matchesStatusName(String actualStatus, String status) {
        AssignmentStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return true;
        }
        return parsedStatus.name().equals(actualStatus);
    }

    private AssignmentStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return AssignmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean matchesText(String q, String... values) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String normalized = q.toLowerCase(Locale.ROOT).trim();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDateRange(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }

    private boolean matchesDateTimeRange(LocalDateTime value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        LocalDate localDate = value.toLocalDate();
        if (from != null && localDate.isBefore(from)) {
            return false;
        }
        return to == null || !localDate.isAfter(to);
    }
}
