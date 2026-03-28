package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.models.TestSession;

@Mapper
public interface TestSessionMapper {
    TestSessionMapper INSTANCE = Mappers.getMapper(TestSessionMapper.class);

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "employee.fullName", target = "employeeFullName")
    @Mapping(source = "test.id", target = "testId")
    @Mapping(source = "test.title", target = "testTitle")
    @Mapping(source = "test.passingScore", target = "testPassingScore")
    @Mapping(source = "test.allowBackNavigation", target = "allowBackNavigation")
    @Mapping(source = "sessionStatus.displayName", target = "sessionStatusDisplayName")
    @Mapping(source = "sessionStatus", target = "sessionStatusName", qualifiedByName = "statusName")
    @Mapping(target = "questionCount", ignore = true)
    @Mapping(target = "answeredCount", ignore = true)
    @Mapping(target = "hasAssignment", ignore = true)
    TestSessionDTO toDTO(TestSession session);

    @Named("statusName")
    default String statusName(TestSessionStatus status) {
        return status != null ? status.name() : null;
    }
}
