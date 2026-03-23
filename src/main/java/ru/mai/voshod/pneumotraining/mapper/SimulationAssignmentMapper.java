package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.SimulationAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.models.SimulationAssignment;
import ru.mai.voshod.pneumotraining.models.SimulationAssignmentEmployee;

import java.util.List;

@Mapper
public interface SimulationAssignmentMapper {
    SimulationAssignmentMapper INSTANCE = Mappers.getMapper(SimulationAssignmentMapper.class);

    @Mapping(source = "scenario.id", target = "scenarioId")
    @Mapping(source = "scenario.title", target = "scenarioTitle")
    @Mapping(source = "assignment", target = "createdByFullName", qualifiedByName = "createdByName")
    @Mapping(source = "assignment", target = "assignmentTitle", qualifiedByName = "assignmentTitle")
    @Mapping(target = "totalAssigned", ignore = true)
    @Mapping(target = "completedCount", ignore = true)
    @Mapping(target = "overdueCount", ignore = true)
    SimulationAssignmentDTO toDTO(SimulationAssignment assignment);

    List<SimulationAssignmentDTO> toDTOList(List<SimulationAssignment> assignments);

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "assignmentEmployee", target = "employeeFullName", qualifiedByName = "empFullName")
    @Mapping(source = "assignmentEmployee", target = "statusName", qualifiedByName = "statusName")
    @Mapping(source = "status.displayName", target = "statusDisplayName")
    @Mapping(source = "completedSimulationSession.id", target = "completedSimulationSessionId")
    SimulationAssignmentEmployeeDTO toEmployeeDTO(SimulationAssignmentEmployee assignmentEmployee);

    List<SimulationAssignmentEmployeeDTO> toEmployeeDTOList(List<SimulationAssignmentEmployee> employees);

    @Named("createdByName")
    default String getCreatedByFullName(SimulationAssignment assignment) {
        return assignment.getCreatedBy().getFullName();
    }

    @Named("empFullName")
    default String getEmployeeFullName(SimulationAssignmentEmployee ae) {
        return ae.getEmployee().getFullName();
    }

    @Named("statusName")
    default String getStatusName(SimulationAssignmentEmployee ae) {
        return ae.getStatus().name();
    }

    @Named("assignmentTitle")
    default String getAssignmentTitle(SimulationAssignment assignment) {
        return assignment.getAssignmentTitle();
    }
}
