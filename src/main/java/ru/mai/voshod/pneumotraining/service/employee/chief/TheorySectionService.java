package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.TheorySectionDTO;
import ru.mai.voshod.pneumotraining.mapper.TheorySectionMapper;
import ru.mai.voshod.pneumotraining.models.TheorySection;
import ru.mai.voshod.pneumotraining.repo.TheoryMaterialRepository;
import ru.mai.voshod.pneumotraining.repo.TheorySectionRepository;

import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TheorySectionService {

    private final TheorySectionRepository theorySectionRepository;
    private final TheoryMaterialRepository theoryMaterialRepository;

    public TheorySectionService(TheorySectionRepository theorySectionRepository,
                                TheoryMaterialRepository theoryMaterialRepository) {
        this.theorySectionRepository = theorySectionRepository;
        this.theoryMaterialRepository = theoryMaterialRepository;
    }

    // ========== CRUD ==========

    @Transactional
    public Optional<Long> saveSection(String title, Integer sortOrder, String description) {
        log.info("Создание раздела теории: title={}", title);

        if (theorySectionRepository.existsByTitle(title)) {
            log.error("Раздел с названием '{}' уже существует", title);
            return Optional.empty();
        }

        try {
            TheorySection section = new TheorySection();
            section.setTitle(title);
            section.setSortOrder(sortOrder);
            section.setDescription(description);
            theorySectionRepository.save(section);
            log.info("Раздел создан: id={}, title={}", section.getId(), title);
            return Optional.of(section.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании раздела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editSection(Long id, String title, Integer sortOrder, String description) {
        log.info("Редактирование раздела теории: id={}", id);

        Optional<TheorySection> sectionOptional = theorySectionRepository.findById(id);
        if (sectionOptional.isEmpty()) {
            log.error("Раздел не найден: id={}", id);
            return Optional.empty();
        }

        if (theorySectionRepository.existsByTitleAndIdNot(title, id)) {
            log.error("Раздел с названием '{}' уже существует", title);
            return Optional.empty();
        }

        try {
            TheorySection section = sectionOptional.get();
            section.setTitle(title);
            section.setSortOrder(sortOrder);
            section.setDescription(description);
            theorySectionRepository.save(section);
            log.info("Раздел обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании раздела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteSection(Long id) {
        log.info("Удаление раздела теории: id={}", id);

        Optional<TheorySection> sectionOptional = theorySectionRepository.findById(id);
        if (sectionOptional.isEmpty()) {
            log.error("Раздел не найден: id={}", id);
            return false;
        }

        if (theoryMaterialRepository.countBySectionId(id) > 0) {
            log.error("Невозможно удалить раздел id={}: содержит материалы", id);
            return false;
        }

        try {
            theorySectionRepository.deleteById(id);
            log.info("Раздел удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении раздела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Запросы данных ==========

    public List<TheorySectionDTO> getAllSections() {
        List<TheorySection> sections = theorySectionRepository.findAllByOrderBySortOrderAsc();
        return TheorySectionMapper.INSTANCE.toDTOList(sections);
    }

    public Optional<TheorySectionDTO> getSectionById(Long id) {
        return theorySectionRepository.findById(id)
                .map(TheorySectionMapper.INSTANCE::toDTO);
    }

    // ========== Проверки ==========

    public boolean checkTitle(String title, Long id) {
        if (id != null) {
            return theorySectionRepository.existsByTitleAndIdNot(title, id);
        }
        return theorySectionRepository.existsByTitle(title);
    }
}
