package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.SchemaConnectionDTO;
import ru.mai.voshod.pneumotraining.models.SchemaConnection;

import java.util.List;

@Mapper
public interface SchemaConnectionMapper {
    SchemaConnectionMapper INSTANCE = Mappers.getMapper(SchemaConnectionMapper.class);

    @Mapping(source = "sourceElement.id", target = "sourceElementId")
    @Mapping(source = "targetElement.id", target = "targetElementId")
    SchemaConnectionDTO toDTO(SchemaConnection connection);

    List<SchemaConnectionDTO> toDTOList(List<SchemaConnection> connections);
}
