package ru.mai.voshod.pneumotraining.service.employee.specialist;

import org.springframework.stereotype.Service;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.models.TestAnswer;
import ru.mai.voshod.pneumotraining.models.TestQuestion;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestScoringService {

    public double calculateAnswerRatio(TestSessionAnswer sessionAnswer, TestQuestion question, List<TestAnswer> questionAnswers) {
        QuestionType type = question.getQuestionType();

        return switch (type) {
            case SINGLE_CHOICE -> calculateSingleChoiceRatio(sessionAnswer);
            case MULTIPLE_CHOICE -> calculateMultipleChoiceRatio(sessionAnswer, questionAnswers);
            case SEQUENCE -> calculateSequenceRatio(sessionAnswer, questionAnswers);
            case MATCHING -> calculateMatchingRatio(sessionAnswer, questionAnswers);
            case OPEN_TEXT -> calculateOpenTextRatio(sessionAnswer, questionAnswers);
        };
    }

    public double calculateWeightedPercent(List<TestSessionAnswer> answers, List<TestQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return 0.0;
        }

        Map<Long, TestSessionAnswer> answersByQuestionId = answers == null
                ? Map.of()
                : answers.stream()
                .filter(answer -> answer.getTestQuestion() != null)
                .collect(Collectors.toMap(
                        answer -> answer.getTestQuestion().getId(),
                        answer -> answer,
                        (left, right) -> right
                ));

        double weightedScore = 0.0;
        double maxWeightedScore = 0.0;

        for (TestQuestion question : questions) {
            int difficulty = normalizeDifficulty(question.getDifficultyLevel());
            maxWeightedScore += difficulty;

            TestSessionAnswer answer = answersByQuestionId.get(question.getId());
            double ratio = answer != null && answer.getEarnedScoreRatio() != null
                    ? clamp(answer.getEarnedScoreRatio())
                    : 0.0;
            weightedScore += ratio * difficulty;
        }

        if (maxWeightedScore == 0.0) {
            return 0.0;
        }

        return (weightedScore * 100.0) / maxWeightedScore;
    }

    public String getScoreLevelDisplayName(Double earnedScoreRatio) {
        double ratio = earnedScoreRatio == null ? 0.0 : clamp(earnedScoreRatio);
        if (ratio >= 1.0) {
            return "Полный зачет";
        }
        if (ratio <= 0.0) {
            return "Без зачета";
        }
        return "Частичный зачет";
    }

    private double calculateSingleChoiceRatio(TestSessionAnswer sessionAnswer) {
        if (sessionAnswer.getSelectedAnswers().size() != 1) {
            return 0.0;
        }
        return sessionAnswer.getSelectedAnswers().get(0).isCorrect() ? 1.0 : 0.0;
    }

    private double calculateMultipleChoiceRatio(TestSessionAnswer sessionAnswer, List<TestAnswer> questionAnswers) {
        Set<Long> correctIds = questionAnswers.stream()
                .filter(TestAnswer::isCorrect)
                .map(TestAnswer::getId)
                .collect(Collectors.toSet());
        if (correctIds.isEmpty()) {
            return 0.0;
        }

        Set<Long> selectedIds = sessionAnswer.getSelectedAnswers().stream()
                .map(TestAnswer::getId)
                .collect(Collectors.toSet());

        long correctSelected = selectedIds.stream().filter(correctIds::contains).count();
        long wrongSelected = selectedIds.size() - correctSelected;

        return clamp(Math.max(0.0, correctSelected - wrongSelected) / correctIds.size());
    }

    private double calculateSequenceRatio(TestSessionAnswer sessionAnswer, List<TestAnswer> questionAnswers) {
        if (sessionAnswer.getAnswerText() == null || sessionAnswer.getAnswerText().isBlank()) {
            return 0.0;
        }

        List<Long> correctOrder = questionAnswers.stream()
                .sorted(Comparator.comparingInt(TestAnswer::getSortOrder))
                .map(TestAnswer::getId)
                .toList();
        if (correctOrder.isEmpty()) {
            return 0.0;
        }

        try {
            List<Long> userOrder = Arrays.stream(sessionAnswer.getAnswerText().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(Long::parseLong)
                    .toList();

            int positions = Math.min(correctOrder.size(), userOrder.size());
            int correctPositions = 0;
            for (int i = 0; i < positions; i++) {
                if (Objects.equals(correctOrder.get(i), userOrder.get(i))) {
                    correctPositions++;
                }
            }
            return clamp((double) correctPositions / correctOrder.size());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double calculateMatchingRatio(TestSessionAnswer sessionAnswer, List<TestAnswer> questionAnswers) {
        if (sessionAnswer.getAnswerText() == null || sessionAnswer.getAnswerText().isBlank()) {
            return 0.0;
        }
        if (questionAnswers.isEmpty()) {
            return 0.0;
        }

        Map<Long, String> userPairs = new HashMap<>();
        try {
            for (String pair : sessionAnswer.getAnswerText().split("\\|\\|\\|")) {
                if (pair.isBlank()) {
                    continue;
                }
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    userPairs.put(Long.parseLong(parts[0].trim()), parts[1].trim());
                }
            }
        } catch (NumberFormatException e) {
            return 0.0;
        }

        long correctPairs = questionAnswers.stream()
                .filter(answer -> Objects.equals(userPairs.get(answer.getId()), safeTrim(answer.getMatchTarget())))
                .count();

        return clamp((double) correctPairs / questionAnswers.size());
    }

    private double calculateOpenTextRatio(TestSessionAnswer sessionAnswer, List<TestAnswer> questionAnswers) {
        String userAnswer = safeTrim(sessionAnswer.getAnswerText());
        if (userAnswer == null || userAnswer.isBlank()) {
            return 0.0;
        }

        return questionAnswers.stream()
                .filter(TestAnswer::isCorrect)
                .map(TestAnswer::getAnswerText)
                .map(this::safeTrim)
                .filter(Objects::nonNull)
                .anyMatch(correct -> correct.equalsIgnoreCase(userAnswer))
                ? 1.0
                : 0.0;
    }

    private int normalizeDifficulty(Integer difficultyLevel) {
        if (difficultyLevel == null) {
            return 1;
        }
        return Math.max(1, Math.min(3, difficultyLevel));
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
