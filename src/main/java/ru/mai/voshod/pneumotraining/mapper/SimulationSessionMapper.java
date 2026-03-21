package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.SimulationSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.SimulationSessionStatus;
import ru.mai.voshod.pneumotraining.models.SimulationSession;

import java.util.List;

@Mapper
public interface SimulationSessionMapper {
    SimulationSessionMapper INSTANCE = Mappers.getMapper(SimulationSessionMapper.class);

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "employee.fullName", target = "employeeFullName")
    @Mapping(source = "scenario.id", target = "scenarioId")
    @Mapping(source = "scenario.title", target = "scenarioTitle")
    @Mapping(source = "sessionStatus", target = "sessionStatus", qualifiedByName = "statusToString")
    @Mapping(source = "sessionStatus", target = "sessionStatusDisplayName", qualifiedByName = "statusToDisplayName")
    @Mapping(source = "scenario.schema.id", target = "schemaId")
    @Mapping(target = "currentState", ignore = true)
    @Mapping(target = "currentInstruction", ignore = true)
    SimulationSessionDTO toDTO(SimulationSession session);

    List<SimulationSessionDTO> toDTOList(List<SimulationSession> sessions);

    @Named("statusToString")
    default String statusToString(SimulationSessionStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("statusToDisplayName")
    default String statusToDisplayName(SimulationSessionStatus status) {
        return status != null ? status.getDisplayName() : null;
    }
}
