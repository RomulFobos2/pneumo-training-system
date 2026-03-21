package ru.mai.voshod.pneumotraining.service.employee.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.DepartmentDTO;
import ru.mai.voshod.pneumotraining.mapper.DepartmentMapper;
import ru.mai.voshod.pneumotraining.models.Department;
import ru.mai.voshod.pneumotraining.repo.DepartmentRepository;
import ru.mai.voshod.pneumotraining.repo.EmployeeRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartments() {
        List<Department> departments = departmentRepository.findAllByOrderByNameAsc();
        List<DepartmentDTO> dtos = DepartmentMapper.INSTANCE.toDTOList(departments);
        for (int i = 0; i < departments.size(); i++) {
            dtos.get(i).setEmployeeCount(departments.get(i).getEmployees().size());
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentDTO> getDepartmentById(Long id) {
        return departmentRepository.findById(id).map(dept -> {
            DepartmentDTO dto = DepartmentMapper.INSTANCE.toDTO(dept);
            dto.setEmployeeCount(dept.getEmployees().size());
            return dto;
        });
    }

    @Transactional
    public Optional<Long> saveDepartment(String name, String description) {
        log.info("Создание подразделения: name={}", name);

        if (departmentRepository.existsByName(name)) {
            log.error("Подразделение с именем '{}' уже существует", name);
            return Optional.empty();
        }

        try {
            Department department = new Department(name, description);
            departmentRepository.save(department);
            log.info("Подразделение создано: id={}, name={}", department.getId(), name);
            return Optional.of(department.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании подразделения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editDepartment(Long id, String name, String description) {
        log.info("Редактирование подразделения: id={}", id);

        Optional<Department> deptOptional = departmentRepository.findById(id);
        if (deptOptional.isEmpty()) {
            log.error("Подразделение не найдено: id={}", id);
            return Optional.empty();
        }

        if (departmentRepository.existsByNameAndIdNot(name, id)) {
            log.error("Подразделение с именем '{}' уже существует", name);
            return Optional.empty();
        }

        try {
            Department department = deptOptional.get();
            department.setName(name);
            department.setDescription(description);
            departmentRepository.save(department);
            log.info("Подразделение обновлено: id={}, name={}", id, name);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании подразделения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteDepartment(Long id) {
        log.info("Удаление подразделения: id={}", id);

        Optional<Department> deptOptional = departmentRepository.findById(id);
        if (deptOptional.isEmpty()) {
            log.error("Подразделение не найдено: id={}", id);
            return false;
        }

        Department department = deptOptional.get();
        if (!department.getEmployees().isEmpty()) {
            log.error("Нельзя удалить подразделение с сотрудниками: id={}, employees={}", id, department.getEmployees().size());
            return false;
        }

        try {
            departmentRepository.delete(department);
            log.info("Подразделение удалено: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении подразделения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    public boolean checkName(String name) {
        return departmentRepository.existsByName(name);
    }

    public boolean checkNameExcluding(String name, Long id) {
        return departmentRepository.existsByNameAndIdNot(name, id);
    }
}
