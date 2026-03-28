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

import java.util.*;

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
        return getAllDepartmentsFlat();
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartmentsTree() {
        List<Department> roots = departmentRepository.findByParentIsNullOrderByNameAsc();
        List<DepartmentDTO> tree = new ArrayList<>();
        for (Department root : roots) {
            tree.add(buildTree(root, 0));
        }
        return tree;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartmentsFlat() {
        List<DepartmentDTO> tree = getAllDepartmentsTree();
        List<DepartmentDTO> flat = new ArrayList<>();
        flattenTree(tree, flat);
        return flat;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartmentsFlatExcluding(Long excludeId) {
        List<DepartmentDTO> flat = getAllDepartmentsFlat();
        Set<Long> excludeIds = new HashSet<>();
        collectDescendantIds(flat, excludeId, excludeIds);
        excludeIds.add(excludeId);
        return flat.stream().filter(d -> !excludeIds.contains(d.getId())).toList();
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
    public Optional<Long> saveDepartment(String name, String description, Long parentId) {
        log.info("Создание подразделения: name={}, parentId={}", name, parentId);

        if (departmentRepository.existsByName(name)) {
            log.error("Подразделение с именем '{}' уже существует", name);
            return Optional.empty();
        }

        try {
            Department department = new Department(name, description);
            if (parentId != null) {
                departmentRepository.findById(parentId).ifPresent(department::setParent);
            }
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
    public Optional<Long> editDepartment(Long id, String name, String description, Long parentId) {
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

            if (parentId != null && !parentId.equals(id)) {
                departmentRepository.findById(parentId).ifPresent(department::setParent);
            } else {
                department.setParent(null);
            }

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
            log.error("Нельзя удалить подразделение с сотрудниками: id={}", id);
            return false;
        }

        if (!department.getChildren().isEmpty()) {
            log.error("Нельзя удалить подразделение с дочерними подразделениями: id={}", id);
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

    /**
     * Собирает ID подразделения и всех его потомков.
     */
    @Transactional(readOnly = true)
    public List<Long> getDescendantIdsIncludingSelf(Long departmentId) {
        List<Long> result = new ArrayList<>();
        result.add(departmentId);
        List<DepartmentDTO> flat = getAllDepartmentsFlat();
        Set<Long> descendants = new HashSet<>();
        collectDescendantIds(flat, departmentId, descendants);
        result.addAll(descendants);
        return result;
    }

    /**
     * Собирает цепочку ID подразделений от заданного до корня (включая само подразделение).
     * Используется для наследования доступа к тестам: если тест назначен на родителя, он доступен потомкам.
     */
    @Transactional(readOnly = true)
    public List<Long> getAncestorIds(Long departmentId) {
        List<Long> ids = new ArrayList<>();
        Optional<Department> current = departmentRepository.findById(departmentId);
        while (current.isPresent()) {
            ids.add(current.get().getId());
            current = current.get().getParent() != null
                    ? Optional.of(current.get().getParent())
                    : Optional.empty();
        }
        return ids;
    }

    // ========== Вспомогательные методы ==========

    private DepartmentDTO buildTree(Department dept, int level) {
        DepartmentDTO dto = DepartmentMapper.INSTANCE.toDTO(dept);
        int ownEmployees = dept.getEmployees().size();
        dto.setLevel(level);
        int childrenTotal = 0;
        List<Department> children = dept.getChildren();
        if (children != null) {
            for (Department child : children.stream()
                    .sorted(Comparator.comparing(Department::getName)).toList()) {
                DepartmentDTO childDto = buildTree(child, level + 1);
                dto.getChildren().add(childDto);
                childrenTotal += childDto.getEmployeeCount();
            }
        }
        dto.setEmployeeCount(ownEmployees + childrenTotal);
        return dto;
    }

    private void flattenTree(List<DepartmentDTO> tree, List<DepartmentDTO> flat) {
        for (DepartmentDTO dto : tree) {
            flat.add(dto);
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                flattenTree(dto.getChildren(), flat);
            }
        }
    }

    private void collectDescendantIds(List<DepartmentDTO> flat, Long parentId, Set<Long> result) {
        for (DepartmentDTO dto : flat) {
            if (parentId.equals(dto.getParentId())) {
                result.add(dto.getId());
                collectDescendantIds(flat, dto.getId(), result);
            }
        }
    }
}
