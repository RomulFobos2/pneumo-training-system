package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.ScenarioStepDTO;
import ru.mai.voshod.pneumotraining.models.ScenarioStep;

import java.util.List;

@Mapper
public interface ScenarioStepMapper {
    ScenarioStepMapper INSTANCE = Mappers.getMapper(ScenarioStepMapper.class);

    ScenarioStepDTO toDTO(ScenarioStep step);

    List<ScenarioStepDTO> toDTOList(List<ScenarioStep> steps);
}
