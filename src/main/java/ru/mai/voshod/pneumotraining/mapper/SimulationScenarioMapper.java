package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.SimulationScenarioDTO;
import ru.mai.voshod.pneumotraining.models.SimulationScenario;

import java.util.List;

@Mapper
public interface SimulationScenarioMapper {
    SimulationScenarioMapper INSTANCE = Mappers.getMapper(SimulationScenarioMapper.class);

    @Mapping(source = "schema.id", target = "schemaId")
    @Mapping(source = "schema.title", target = "schemaTitle")
    @Mapping(source = "createdBy.id", target = "createdById")
    @Mapping(source = "createdBy.fullName", target = "createdByFullName")
    @Mapping(target = "scenarioTypeName", expression = "java(scenario.getScenarioType().name())")
    @Mapping(source = "scenarioType.displayName", target = "scenarioTypeDisplayName")
    @Mapping(source = "parentScenario.id", target = "parentScenarioId")
    @Mapping(source = "parentScenario.title", target = "parentScenarioTitle")
    @Mapping(target = "stepCount", ignore = true)
    @Mapping(target = "departmentIds", ignore = true)
    @Mapping(target = "allowedDepartments", ignore = true)
    @Mapping(target = "faultScenarioCount", ignore = true)
    @Mapping(target = "faultScenariosList", ignore = true)
    SimulationScenarioDTO toDTO(SimulationScenario scenario);

    List<SimulationScenarioDTO> toDTOList(List<SimulationScenario> scenarios);
}
