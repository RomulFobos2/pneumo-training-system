package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.SchemaElementDTO;
import ru.mai.voshod.pneumotraining.models.SchemaElement;

import java.util.List;

@Mapper
public interface SchemaElementMapper {
    SchemaElementMapper INSTANCE = Mappers.getMapper(SchemaElementMapper.class);

    @Mapping(target = "elementType", expression = "java(element.getElementType().name())")
    SchemaElementDTO toDTO(SchemaElement element);

    List<SchemaElementDTO> toDTOList(List<SchemaElement> elements);
}
