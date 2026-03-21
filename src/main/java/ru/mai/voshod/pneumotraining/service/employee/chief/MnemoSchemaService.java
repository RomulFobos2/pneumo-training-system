package ru.mai.voshod.pneumotraining.service.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.voshod.pneumotraining.dto.MnemoSchemaDTO;
import ru.mai.voshod.pneumotraining.dto.SchemaConnectionDTO;
import ru.mai.voshod.pneumotraining.dto.SchemaDataDTO;
import ru.mai.voshod.pneumotraining.dto.SchemaElementDTO;
import ru.mai.voshod.pneumotraining.enumeration.ElementType;
import ru.mai.voshod.pneumotraining.mapper.MnemoSchemaMapper;
import ru.mai.voshod.pneumotraining.mapper.SchemaConnectionMapper;
import ru.mai.voshod.pneumotraining.mapper.SchemaElementMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.MnemoSchema;
import ru.mai.voshod.pneumotraining.models.SchemaConnection;
import ru.mai.voshod.pneumotraining.models.SchemaElement;
import ru.mai.voshod.pneumotraining.repo.MnemoSchemaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MnemoSchemaService {

    private final MnemoSchemaRepository mnemoSchemaRepository;

    public MnemoSchemaService(MnemoSchemaRepository mnemoSchemaRepository) {
        this.mnemoSchemaRepository = mnemoSchemaRepository;
    }

    // ========== CRUD ==========

    @Transactional(readOnly = true)
    public List<MnemoSchemaDTO> getAllSchemas() {
        List<MnemoSchema> schemas = mnemoSchemaRepository.findAllByOrderByTitleAsc();
        return schemas.stream().map(this::toSchemaDTO).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MnemoSchemaDTO> getSchemaById(Long id) {
        return mnemoSchemaRepository.findById(id).map(this::toSchemaDTO);
    }

    @Transactional
    public Optional<Long> saveSchema(String title, String description, Integer width, Integer height,
                                      Employee createdBy) {
        log.info("Создание схемы: title={}", title);
        try {
            if (mnemoSchemaRepository.existsByTitle(title)) {
                log.warn("Схема с названием '{}' уже существует", title);
                return Optional.empty();
            }
            MnemoSchema schema = new MnemoSchema();
            schema.setTitle(title);
            schema.setDescription(description);
            schema.setWidth(width != null ? width : 1200);
            schema.setHeight(height != null ? height : 800);
            schema.setCreatedBy(createdBy);
            mnemoSchemaRepository.save(schema);
            log.info("Схема создана: id={}", schema.getId());
            return Optional.of(schema.getId());
        } catch (Exception e) {
            log.error("Ошибка создания схемы", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editSchema(Long id, String title, String description, Integer width, Integer height) {
        log.info("Редактирование схемы: id={}", id);
        try {
            Optional<MnemoSchema> schemaOpt = mnemoSchemaRepository.findById(id);
            if (schemaOpt.isEmpty()) return Optional.empty();

            if (mnemoSchemaRepository.existsByTitleAndIdNot(title, id)) {
                log.warn("Схема с названием '{}' уже существует", title);
                return Optional.empty();
            }

            MnemoSchema schema = schemaOpt.get();
            schema.setTitle(title);
            schema.setDescription(description);
            schema.setWidth(width != null ? width : 1200);
            schema.setHeight(height != null ? height : 800);
            mnemoSchemaRepository.save(schema);
            log.info("Схема обновлена: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка редактирования схемы", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteSchema(Long id) {
        log.info("Удаление схемы: id={}", id);
        try {
            Optional<MnemoSchema> schemaOpt = mnemoSchemaRepository.findById(id);
            if (schemaOpt.isEmpty()) return false;
            mnemoSchemaRepository.delete(schemaOpt.get());
            log.info("Схема удалена: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка удаления схемы", e);
            return false;
        }
    }

    public boolean checkTitle(String title, Long excludeId) {
        if (excludeId != null) {
            return mnemoSchemaRepository.existsByTitleAndIdNot(title, excludeId);
        }
        return mnemoSchemaRepository.existsByTitle(title);
    }

    // ========== Данные схемы (элементы + соединения) ==========

    @Transactional(readOnly = true)
    public SchemaDataDTO loadSchemaData(Long schemaId) {
        Optional<MnemoSchema> schemaOpt = mnemoSchemaRepository.findById(schemaId);
        if (schemaOpt.isEmpty()) return new SchemaDataDTO();

        MnemoSchema schema = schemaOpt.get();
        SchemaDataDTO data = new SchemaDataDTO();
        data.setWidth(schema.getWidth());
        data.setHeight(schema.getHeight());
        data.setElements(SchemaElementMapper.INSTANCE.toDTOList(schema.getElements()));
        data.setConnections(SchemaConnectionMapper.INSTANCE.toDTOList(schema.getConnections()));
        return data;
    }

    @Transactional
    public boolean saveSchemaData(Long schemaId, SchemaDataDTO data) {
        log.info("Сохранение данных схемы: id={}, elements={}, connections={}",
                schemaId, data.getElements().size(), data.getConnections().size());
        try {
            Optional<MnemoSchema> schemaOpt = mnemoSchemaRepository.findById(schemaId);
            if (schemaOpt.isEmpty()) return false;

            MnemoSchema schema = schemaOpt.get();

            if (data.getWidth() != null) schema.setWidth(data.getWidth());
            if (data.getHeight() != null) schema.setHeight(data.getHeight());

            // 1. Очистить ОБЕ коллекции и flush (orphanRemoval удалит старые записи)
            schema.getConnections().clear();
            schema.getElements().clear();
            mnemoSchemaRepository.saveAndFlush(schema);

            // 2. Добавить новые элементы
            Map<Long, SchemaElement> tempIdMap = new HashMap<>();
            for (SchemaElementDTO dto : data.getElements()) {
                SchemaElement element = new SchemaElement();
                element.setName(dto.getName());
                element.setElementType(ElementType.valueOf(dto.getElementType()));
                element.setPosX(dto.getPosX());
                element.setPosY(dto.getPosY());
                element.setWidth(dto.getWidth() != null ? dto.getWidth() : 60.0);
                element.setHeight(dto.getHeight() != null ? dto.getHeight() : 60.0);
                element.setInitialState(dto.isInitialState());
                element.setRotation(dto.getRotation() != null ? dto.getRotation() : 0);
                element.setSchema(schema);
                schema.getElements().add(element);
                if (dto.getId() != null) {
                    tempIdMap.put(dto.getId(), element);
                }
            }

            // 3. Flush чтобы элементы получили real ID
            mnemoSchemaRepository.saveAndFlush(schema);

            // 4. Добавить соединения (элементы уже persisted)
            for (SchemaConnectionDTO dto : data.getConnections()) {
                SchemaElement source = tempIdMap.get(dto.getSourceElementId());
                SchemaElement target = tempIdMap.get(dto.getTargetElementId());
                if (source == null || target == null) continue;

                SchemaConnection conn = new SchemaConnection();
                conn.setSchema(schema);
                conn.setSourceElement(source);
                conn.setTargetElement(target);
                conn.setPathData(dto.getPathData());
                schema.getConnections().add(conn);
            }

            mnemoSchemaRepository.save(schema);
            log.info("Данные схемы сохранены: id={}", schemaId);
            return true;
        } catch (Exception e) {
            log.error("Ошибка сохранения данных схемы", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Вспомогательные ==========

    private MnemoSchemaDTO toSchemaDTO(MnemoSchema schema) {
        MnemoSchemaDTO dto = MnemoSchemaMapper.INSTANCE.toDTO(schema);
        dto.setElementCount(schema.getElements() != null ? schema.getElements().size() : 0);
        dto.setConnectionCount(schema.getConnections() != null ? schema.getConnections().size() : 0);
        return dto;
    }
}
