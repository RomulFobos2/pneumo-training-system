package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.MnemoSchemaDTO;
import ru.mai.voshod.pneumotraining.models.MnemoSchema;

import java.util.List;

@Mapper
public interface MnemoSchemaMapper {
    MnemoSchemaMapper INSTANCE = Mappers.getMapper(MnemoSchemaMapper.class);

    @Mapping(source = "createdBy.id", target = "createdById")
    @Mapping(source = "createdBy.fullName", target = "createdByFullName")
    @Mapping(target = "elementCount", ignore = true)
    @Mapping(target = "connectionCount", ignore = true)
    MnemoSchemaDTO toDTO(MnemoSchema schema);

    List<MnemoSchemaDTO> toDTOList(List<MnemoSchema> schemas);
}
