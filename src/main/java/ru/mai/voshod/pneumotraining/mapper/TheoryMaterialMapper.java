package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.enumeration.MaterialType;
import ru.mai.voshod.pneumotraining.models.TheoryMaterial;

import java.util.List;

@Mapper
public interface TheoryMaterialMapper {
    TheoryMaterialMapper INSTANCE = Mappers.getMapper(TheoryMaterialMapper.class);

    @Mapping(source = "materialType.displayName", target = "materialTypeDisplayName")
    @Mapping(source = "materialType", target = "materialTypeName", qualifiedByName = "materialTypeName")
    @Mapping(source = "section.id", target = "sectionId")
    @Mapping(source = "section.title", target = "sectionTitle")
    TheoryMaterialDTO toDTO(TheoryMaterial material);

    List<TheoryMaterialDTO> toDTOList(List<TheoryMaterial> materials);

    @Named("materialTypeName")
    default String materialTypeName(MaterialType type) {
        return type != null ? type.name() : null;
    }
}
