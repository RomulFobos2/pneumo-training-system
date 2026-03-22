package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentDTO;
import ru.mai.voshod.pneumotraining.dto.TestAssignmentEmployeeDTO;
import ru.mai.voshod.pneumotraining.models.TestAssignment;
import ru.mai.voshod.pneumotraining.models.TestAssignmentEmployee;

import java.util.List;

@Mapper
public interface TestAssignmentMapper {
    TestAssignmentMapper INSTANCE = Mappers.getMapper(TestAssignmentMapper.class);

    @Mapping(source = "test.id", target = "testId")
    @Mapping(source = "test.title", target = "testTitle")
    @Mapping(source = "scenario.id", target = "scenarioId")
    @Mapping(source = "scenario.title", target = "scenarioTitle")
    @Mapping(source = "assignment", target = "createdByFullName", qualifiedByName = "createdByName")
    @Mapping(source = "assignment", target = "assignmentType", qualifiedByName = "assignmentType")
    @Mapping(source = "assignment", target = "assignmentTitle", qualifiedByName = "assignmentTitle")
    @Mapping(target = "totalAssigned", ignore = true)
    @Mapping(target = "completedCount", ignore = true)
    @Mapping(target = "overdueCount", ignore = true)
    TestAssignmentDTO toDTO(TestAssignment assignment);

    List<TestAssignmentDTO> toDTOList(List<TestAssignment> assignments);

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "assignmentEmployee", target = "employeeFullName", qualifiedByName = "empFullName")
    @Mapping(source = "assignmentEmployee", target = "statusName", qualifiedByName = "statusName")
    @Mapping(source = "status.displayName", target = "statusDisplayName")
    @Mapping(source = "completedSession.id", target = "completedSessionId")
    TestAssignmentEmployeeDTO toEmployeeDTO(TestAssignmentEmployee assignmentEmployee);

    List<TestAssignmentEmployeeDTO> toEmployeeDTOList(List<TestAssignmentEmployee> employees);

    @Named("createdByName")
    default String getCreatedByFullName(TestAssignment assignment) {
        return assignment.getCreatedBy().getFullName();
    }

    @Named("empFullName")
    default String getEmployeeFullName(TestAssignmentEmployee ae) {
        return ae.getEmployee().getFullName();
    }

    @Named("statusName")
    default String getStatusName(TestAssignmentEmployee ae) {
        return ae.getStatus().name();
    }

    @Named("assignmentType")
    default String getAssignmentType(TestAssignment assignment) {
        return assignment.getAssignmentType();
    }

    @Named("assignmentTitle")
    default String getAssignmentTitle(TestAssignment assignment) {
        return assignment.getAssignmentTitle();
    }
}
