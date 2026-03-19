package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;
import ru.mai.voshod.pneumotraining.mapper.TheoryMaterialMapper;
import ru.mai.voshod.pneumotraining.models.TheoryMaterial;
import ru.mai.voshod.pneumotraining.models.TheorySection;
import ru.mai.voshod.pneumotraining.repo.TheoryMaterialRepository;
import ru.mai.voshod.pneumotraining.repo.TheorySectionRepository;
import ru.mai.voshod.pneumotraining.service.general.FileStorageService;

import java.util.List;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TheoryMaterialService {

    private final TheoryMaterialRepository theoryMaterialRepository;
    private final TheorySectionRepository theorySectionRepository;
    private final FileStorageService fileStorageService;

    public TheoryMaterialService(TheoryMaterialRepository theoryMaterialRepository,
                                 TheorySectionRepository theorySectionRepository,
                                 FileStorageService fileStorageService) {
        this.theoryMaterialRepository = theoryMaterialRepository;
        this.theorySectionRepository = theorySectionRepository;
        this.fileStorageService = fileStorageService;
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
    public Optional<Long> saveMaterialWithFile(Long sectionId, String title, MultipartFile file,
                                                Integer sortOrder) {
        log.info("Создание PDF-материала с файлом: title={}, sectionId={}", title, sectionId);
        String filename = fileStorageService.saveFile(file);
        if (filename == null) {
            log.error("Не удалось сохранить файл");
            return Optional.empty();
        }
        return saveMaterial(sectionId, title, filename, sortOrder, "PDF");
    }

    @Transactional
    public Optional<Long> editMaterialWithFile(Long id, String title, MultipartFile file,
                                                Integer sortOrder) {
        log.info("Редактирование PDF-материала с новым файлом: id={}", id);
        Optional<TheoryMaterial> oldMaterial = theoryMaterialRepository.findById(id);
        if (oldMaterial.isPresent() && oldMaterial.get().getMaterialType() == MaterialType.PDF) {
            fileStorageService.deleteFile(oldMaterial.get().getContent());
        }
        String filename = fileStorageService.saveFile(file);
        if (filename == null) {
            log.error("Не удалось сохранить файл");
            return Optional.empty();
        }
        return editMaterial(id, title, filename, sortOrder, "PDF");
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
            // Если тип менялся с PDF на другой — удаляем старый файл
            if (material.getMaterialType() == MaterialType.PDF && materialType != MaterialType.PDF) {
                fileStorageService.deleteFile(material.getContent());
            }
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
            TheoryMaterial material = materialOptional.get();
            if (material.getMaterialType() == MaterialType.PDF) {
                fileStorageService.deleteFile(material.getContent());
            }
            theoryMaterialRepository.deleteById(id);
            log.info("Материал удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении материала: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Перестановка ==========

    @Transactional
    public boolean reorderMaterials(Long sectionId, List<Long> orderedIds) {
        log.info("Перестановка материалов раздела {}: {}", sectionId, orderedIds);
        try {
            for (int i = 0; i < orderedIds.size(); i++) {
                Optional<TheoryMaterial> materialOpt = theoryMaterialRepository.findById(orderedIds.get(i));
                if (materialOpt.isEmpty()) {
                    log.error("Материал не найден: id={}", orderedIds.get(i));
                    return false;
                }
                materialOpt.get().setSortOrder(i + 1);
                theoryMaterialRepository.save(materialOpt.get());
            }
            log.info("Материалы переупорядочены успешно");
            return true;
        } catch (Exception e) {
            log.error("Ошибка при переупорядочивании материалов: {}", e.getMessage(), e);
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
