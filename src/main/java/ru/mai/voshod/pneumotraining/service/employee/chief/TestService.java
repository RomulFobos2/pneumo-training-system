package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.mapper.TestMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Test;
import ru.mai.voshod.pneumotraining.repo.TestQuestionRepository;
import ru.mai.voshod.pneumotraining.repo.TestRepository;

import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TestService {

    private final TestRepository testRepository;
    private final TestQuestionRepository testQuestionRepository;

    public TestService(TestRepository testRepository,
                       TestQuestionRepository testQuestionRepository) {
        this.testRepository = testRepository;
        this.testQuestionRepository = testQuestionRepository;
    }

    // ========== CRUD ==========

    @Transactional
    public Optional<Long> saveTest(String title, String description, Integer timeLimit,
                                    Integer passingScore, boolean isExam, boolean allowBackNavigation,
                                    Employee createdBy) {
        log.info("Создание теста: title={}", title);

        if (testRepository.existsByTitle(title)) {
            log.error("Тест с названием '{}' уже существует", title);
            return Optional.empty();
        }

        try {
            Test test = new Test();
            test.setTitle(title);
            test.setDescription(description);
            test.setTimeLimit(timeLimit != null ? timeLimit : 0);
            test.setPassingScore(passingScore != null ? passingScore : 60);
            test.setExam(isExam);
            test.setAllowBackNavigation(allowBackNavigation);
            test.setActive(false);
            test.setCreatedBy(createdBy);
            testRepository.save(test);
            log.info("Тест создан: id={}, title={}", test.getId(), title);
            return Optional.of(test.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании теста: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editTest(Long id, String title, String description, Integer timeLimit,
                                    Integer passingScore, boolean isExam, boolean allowBackNavigation) {
        log.info("Редактирование теста: id={}", id);

        Optional<Test> testOptional = testRepository.findById(id);
        if (testOptional.isEmpty()) {
            log.error("Тест не найден: id={}", id);
            return Optional.empty();
        }

        if (testRepository.existsByTitleAndIdNot(title, id)) {
            log.error("Тест с названием '{}' уже существует", title);
            return Optional.empty();
        }

        try {
            Test test = testOptional.get();
            test.setTitle(title);
            test.setDescription(description);
            test.setTimeLimit(timeLimit != null ? timeLimit : 0);
            test.setPassingScore(passingScore != null ? passingScore : 60);
            test.setExam(isExam);
            test.setAllowBackNavigation(allowBackNavigation);
            testRepository.save(test);
            log.info("Тест обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании теста: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteTest(Long id) {
        log.info("Удаление теста: id={}", id);

        Optional<Test> testOptional = testRepository.findById(id);
        if (testOptional.isEmpty()) {
            log.error("Тест не найден: id={}", id);
            return false;
        }

        try {
            testRepository.deleteById(id);
            log.info("Тест удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении теста: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional
    public boolean toggleActive(Long id) {
        log.info("Переключение активности теста: id={}", id);

        Optional<Test> testOptional = testRepository.findById(id);
        if (testOptional.isEmpty()) {
            log.error("Тест не найден: id={}", id);
            return false;
        }

        try {
            Test test = testOptional.get();

            // Нельзя активировать тест без вопросов
            if (!test.isActive() && testQuestionRepository.countByTestId(id) == 0) {
                log.error("Невозможно активировать тест без вопросов: id={}", id);
                return false;
            }

            test.setActive(!test.isActive());
            testRepository.save(test);
            log.info("Тест id={} теперь {}", id, test.isActive() ? "активен" : "неактивен");
            return true;
        } catch (Exception e) {
            log.error("Ошибка при переключении активности теста: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Запросы данных ==========

    public List<TestDTO> getAllTests() {
        List<Test> tests = testRepository.findAllByOrderByIdDesc();
        return tests.stream().map(test -> {
            TestDTO dto = TestMapper.INSTANCE.toDTO(test);
            dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
            return dto;
        }).toList();
    }

    public List<TestDTO> getAllActiveTests() {
        List<Test> tests = testRepository.findByIsActiveTrueOrderByTitleAsc();
        return tests.stream().map(test -> {
            TestDTO dto = TestMapper.INSTANCE.toDTO(test);
            dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TestDTO> getTestById(Long id) {
        return testRepository.findById(id)
                .map(test -> {
                    TestDTO dto = TestMapper.INSTANCE.toDTO(test);
                    dto.setQuestionCount((int) testQuestionRepository.countByTestId(test.getId()));
                    return dto;
                });
    }

    // ========== Проверки ==========

    public boolean checkTitle(String title, Long id) {
        if (id != null) {
            return testRepository.existsByTitleAndIdNot(title, id);
        }
        return testRepository.existsByTitle(title);
    }
}
