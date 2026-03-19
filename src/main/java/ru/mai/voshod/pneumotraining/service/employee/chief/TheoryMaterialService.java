package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;
import ru.mai.voshod.pneumotraining.mapper.TheoryMaterialMapper;
import ru.mai.voshod.pneumotraining.models.TheoryMaterial;
import ru.mai.voshod.pneumotraining.models.TheorySection;
import ru.mai.voshod.pneumotraining.repo.TheoryMaterialRepository;
import ru.mai.voshod.pneumotraining.repo.TheorySectionRepository;

import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TheoryMaterialService {

    private final TheoryMaterialRepository theoryMaterialRepository;
    private final TheorySectionRepository theorySectionRepository;

    public TheoryMaterialService(TheoryMaterialRepository theoryMaterialRepository,
                                 TheorySectionRepository theorySectionRepository) {
        this.theoryMaterialRepository = theoryMaterialRepository;
        this.theorySectionRepository = theorySectionRepository;
    }

    // ========== CRUD ==========

    @Transactional
    public Optional<Long> saveMaterial(Long sectionId, String title, String content,
                                       Integer sortOrder, String materialTypeName) {
        log.info("Создание материала: title={}, sectionId={}", title, sectionId);

        Optional<TheorySection> sectionOptional = theorySectionRepository.findById(sectionId);
        if (sectionOptional.isEmpty()) {
            log.error("Раздел не найден: id={}", sectionId);
            return Optional.empty();
        }

        try {
            MaterialType materialType = MaterialType.valueOf(materialTypeName);
            TheoryMaterial material = new TheoryMaterial();
            material.setTitle(title);
            material.setContent(content);
            material.setSortOrder(sortOrder);
            material.setMaterialType(materialType);
            material.setSection(sectionOptional.get());
            theoryMaterialRepository.save(material);
            log.info("Материал создан: id={}, title={}", material.getId(), title);
            return Optional.of(material.getId());
        } catch (Exception e) {
            log.error("Ошибка при создании материала: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editMaterial(Long id, String title, String content,
                                       Integer sortOrder, String materialTypeName) {
        log.info("Редактирование материала: id={}", id);

        Optional<TheoryMaterial> materialOptional = theoryMaterialRepository.findById(id);
        if (materialOptional.isEmpty()) {
            log.error("Материал не найден: id={}", id);
            return Optional.empty();
        }

        try {
            MaterialType materialType = MaterialType.valueOf(materialTypeName);
            TheoryMaterial material = materialOptional.get();
            material.setTitle(title);
            material.setContent(content);
            material.setSortOrder(sortOrder);
            material.setMaterialType(materialType);
            theoryMaterialRepository.save(material);
            log.info("Материал обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании материала: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteMaterial(Long id) {
        log.info("Удаление материала: id={}", id);

        Optional<TheoryMaterial> materialOptional = theoryMaterialRepository.findById(id);
        if (materialOptional.isEmpty()) {
            log.error("Материал не найден: id={}", id);
            return false;
        }

        try {
            theoryMaterialRepository.deleteById(id);
            log.info("Материал удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении материала: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Запросы данных ==========

    public List<TheoryMaterialDTO> getMaterialsBySection(Long sectionId) {
        List<TheoryMaterial> materials = theoryMaterialRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        return TheoryMaterialMapper.INSTANCE.toDTOList(materials);
    }

    public Optional<TheoryMaterialDTO> getMaterialById(Long id) {
        return theoryMaterialRepository.findById(id)
                .map(TheoryMaterialMapper.INSTANCE::toDTO);
    }

    public Optional<Long> getSectionIdByMaterialId(Long materialId) {
        return theoryMaterialRepository.findById(materialId)
                .map(m -> m.getSection().getId());
    }
}
