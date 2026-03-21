package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.AssignedTestDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.mapper.TestAssignmentMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.*;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TestAssignmentService {

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
    public List<TestAssignmentDTO> getAllAssignments() {
        List<TestAssignment> assignments = testAssignmentRepository.findAllByOrderByCreatedAtDesc();
        List<TestAssignmentDTO> dtos = new ArrayList<>();
        for (TestAssignment a : assignments) {
            TestAssignmentDTO dto = TestAssignmentMapper.INSTANCE.toDTO(a);
            List<TestAssignmentEmployee> employees = a.getAssignedEmployees();
            dto.setTotalAssigned(employees.size());
            dto.setCompletedCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
            dto.setOverdueCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Optional<TestAssignmentDTO> getAssignmentById(Long id) {
        return testAssignmentRepository.findById(id).map(a -> {
            TestAssignmentDTO dto = TestAssignmentMapper.INSTANCE.toDTO(a);
            List<TestAssignmentEmployee> employees = a.getAssignedEmployees();
            dto.setTotalAssigned(employees.size());
            dto.setCompletedCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.COMPLETED).count());
            dto.setOverdueCount((int) employees.stream()
                    .filter(e -> e.getStatus() == AssignmentStatus.OVERDUE).count());
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public List<TestAssignmentEmployeeDTO> getAssignmentEmployees(Long assignmentId) {
        List<TestAssignmentEmployee> employees = testAssignmentEmployeeRepository.findByAssignmentId(assignmentId);
        return TestAssignmentMapper.INSTANCE.toEmployeeDTOList(employees);
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

    @Transactional(readOnly = true)
    public List<AssignedTestDTO> getAssignedTestsForEmployee(Long employeeId) {
        List<TestAssignmentEmployee> pending = testAssignmentEmployeeRepository
                .findByEmployeeIdAndStatus(employeeId, AssignmentStatus.PENDING);

        List<AssignedTestDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (TestAssignmentEmployee ae : pending) {
            Test test = ae.getAssignment().getTest();
            if (!test.isActive()) continue;

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
            dto.setStatusName(ae.getStatus().name());
            dto.setStatusDisplayName(ae.getStatus().getDisplayName());
            dto.setDaysUntilDeadline(ChronoUnit.DAYS.between(today, ae.getAssignment().getDeadline()));
            result.add(dto);
        }
        return result;
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
}
