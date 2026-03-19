package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TestAnswerDTO;
import ru.mai.voshod.pneumotraining.models.TestAnswer;

import java.util.List;

@Mapper
public interface TestAnswerMapper {
    TestAnswerMapper INSTANCE = Mappers.getMapper(TestAnswerMapper.class);

    @Mapping(source = "question.id", target = "questionId")
    TestAnswerDTO toDTO(TestAnswer answer);

    List<TestAnswerDTO> toDTOList(List<TestAnswer> answers);
}
