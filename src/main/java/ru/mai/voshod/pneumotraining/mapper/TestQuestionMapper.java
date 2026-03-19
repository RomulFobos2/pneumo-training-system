package ru.mai.voshod.pneumotraining.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.TestQuestionDTO;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.models.TestQuestion;

import java.util.List;

@Mapper(uses = TestAnswerMapper.class)
public interface TestQuestionMapper {
    TestQuestionMapper INSTANCE = Mappers.getMapper(TestQuestionMapper.class);

    @Mapping(source = "questionType.displayName", target = "questionTypeDisplayName")
    @Mapping(source = "questionType", target = "questionTypeName", qualifiedByName = "questionTypeName")
    @Mapping(source = "test.id", target = "testId")
    @Mapping(source = "test.title", target = "testTitle")
    @Mapping(target = "answerCount", ignore = true)
    @Mapping(source = "answers", target = "answers")
    TestQuestionDTO toDTO(TestQuestion question);

    List<TestQuestionDTO> toDTOList(List<TestQuestion> questions);

    @Named("questionTypeName")
    default String questionTypeName(QuestionType type) {
        return type != null ? type.name() : null;
    }
}
