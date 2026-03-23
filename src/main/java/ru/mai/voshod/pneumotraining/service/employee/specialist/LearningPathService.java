package ru.mai.voshod.pneumotraining.service.employee.specialist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.LearningRecommendationDTO;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.dto.TheoryMaterialDTO;
import ru.mai.voshod.pneumotraining.dto.WeakTopicDTO;
import ru.mai.voshod.pneumotraining.mapper.TestMapper;
import ru.mai.voshod.pneumotraining.mapper.TheoryMaterialMapper;
import ru.mai.voshod.pneumotraining.models.*;
import ru.mai.voshod.pneumotraining.repo.TestQuestionRepository;
import ru.mai.voshod.pneumotraining.repo.TestSessionAnswerRepository;
import ru.mai.voshod.pneumotraining.repo.TestSessionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LearningPathService {

    private final TestSessionRepository testSessionRepository;
    private final TestSessionAnswerRepository testSessionAnswerRepository;
    private final TestQuestionRepository testQuestionRepository;

    public LearningPathService(TestSessionRepository testSessionRepository,
                               TestSessionAnswerRepository testSessionAnswerRepository,
                               TestQuestionRepository testQuestionRepository) {
        this.testSessionRepository = testSessionRepository;
        this.testSessionAnswerRepository = testSessionAnswerRepository;
        this.testQuestionRepository = testQuestionRepository;
    }

    @Transactional(readOnly = true)
    public LearningRecommendationDTO getRecommendations(Long sessionId, Employee employee) {
        Optional<TestSession> sessionOpt = testSessionRepository.findByIdAndEmployeeId(sessionId, employee.getId());
        if (sessionOpt.isEmpty()) {
            return new LearningRecommendationDTO();
        }
        return buildRecommendations(sessionOpt.get());
    }

    /** Для начальника — без проверки владельца сессии */
    @Transactional(readOnly = true)
    public LearningRecommendationDTO getRecommendationsById(Long sessionId) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return new LearningRecommendationDTO();
        }
        return buildRecommendations(sessionOpt.get());
    }

    private LearningRecommendationDTO buildRecommendations(TestSession session) {
        LearningRecommendationDTO result = new LearningRecommendationDTO();
        Long currentTestId = session.getTest().getId();

        List<TestSessionAnswer> answers = testSessionAnswerRepository.findByTestSessionIdOrderByIdAsc(session.getId());

        result.setTotalQuestions(answers.size());
        result.setCorrectCount((int) answers.stream().filter(TestSessionAnswer::isCorrect).count());
        result.setWrongCount(result.getTotalQuestions() - result.getCorrectCount());

        // Группируем неправильные ответы по разделу теории
        Map<Long, List<TestSessionAnswer>> wrongBySection = new LinkedHashMap<>();
        Map<Long, Integer> totalBySection = new HashMap<>();

        for (TestSessionAnswer answer : answers) {
            TestQuestion question = answer.getTestQuestion();
            if (question.getTheorySection() == null) {
                continue;
            }
            Long sectionId = question.getTheorySection().getId();

            totalBySection.merge(sectionId, 1, Integer::sum);

            if (!answer.isCorrect()) {
                wrongBySection.computeIfAbsent(sectionId, k -> new ArrayList<>()).add(answer);
            }
        }

        // Для каждого слабого раздела формируем рекомендации
        for (Map.Entry<Long, List<TestSessionAnswer>> entry : wrongBySection.entrySet()) {
            Long sectionId = entry.getKey();
            List<TestSessionAnswer> wrongAnswers = entry.getValue();

            TheorySection section = wrongAnswers.get(0).getTestQuestion().getTheorySection();

            WeakTopicDTO topic = new WeakTopicDTO();
            topic.setSectionId(sectionId);
            topic.setSectionTitle(section.getTitle());
            topic.setWrongCount(wrongAnswers.size());
            topic.setTotalInSection(totalBySection.getOrDefault(sectionId, 0));

            // Материалы из этого раздела
            if (section.getMaterials() != null) {
                List<TheoryMaterialDTO> materialDTOs = section.getMaterials().stream()
                        .map(TheoryMaterialMapper.INSTANCE::toDTO)
                        .toList();
                topic.setMaterials(materialDTOs);
            }

            // Тесты с вопросами из этого раздела (кроме текущего теста)
            List<TestQuestion> questionsInSection = testQuestionRepository.findByTheorySectionId(sectionId);
            Set<Test> relatedTests = questionsInSection.stream()
                    .map(TestQuestion::getTest)
                    .filter(t -> !t.getId().equals(currentTestId))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<TestDTO> testDTOs = relatedTests.stream()
                    .map(TestMapper.INSTANCE::toDTO)
                    .toList();
            topic.setSuggestedTests(testDTOs);

            result.getWeakTopics().add(topic);
        }

        // Сортируем по количеству ошибок (по убыванию)
        result.getWeakTopics().sort(Comparator.comparingInt(WeakTopicDTO::getWrongCount).reversed());

        return result;
    }
}
