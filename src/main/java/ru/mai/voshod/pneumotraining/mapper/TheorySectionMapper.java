package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TheorySectionDTO;
import ru.mai.voshod.pneumotraining.models.TheorySection;

import java.util.List;

@Mapper
public interface TheorySectionMapper {
    TheorySectionMapper INSTANCE = Mappers.getMapper(TheorySectionMapper.class);

    @Mapping(source = "section", target = "materialCount", qualifiedByName = "materialCount")
    TheorySectionDTO toDTO(TheorySection section);

    List<TheorySectionDTO> toDTOList(List<TheorySection> sections);

    @Named("materialCount")
    default int materialCount(TheorySection section) {
        return section.getMaterials() != null ? section.getMaterials().size() : 0;
    }
}
