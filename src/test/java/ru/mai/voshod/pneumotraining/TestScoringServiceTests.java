package ru.mai.voshod.pneumotraining;

import org.junit.jupiter.api.Test;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.models.TestAnswer;
import ru.mai.voshod.pneumotraining.models.TestQuestion;
import ru.mai.voshod.pneumotraining.models.TestSessionAnswer;
import ru.mai.voshod.pneumotraining.service.employee.specialist.TestScoringService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestScoringServiceTests {

    private final TestScoringService scoringService = new TestScoringService();

    @Test
    void shouldScoreSingleChoiceAsBinary() {
        TestQuestion question = question(1L, QuestionType.SINGLE_CHOICE, 1);
        TestAnswer correct = answer(11L, "A", true, 1, null, question);
        TestAnswer wrong = answer(12L, "B", false, 2, null, question);

        TestSessionAnswer sessionAnswer = new TestSessionAnswer();
        sessionAnswer.getSelectedAnswers().add(correct);

        assertEquals(1.0, scoringService.calculateAnswerRatio(sessionAnswer, question, List.of(correct, wrong)));
    }

    @Test
    void shouldScoreMultipleChoiceWithSoftPenalty() {
        TestQuestion question = question(2L, QuestionType.MULTIPLE_CHOICE, 2);
        TestAnswer correct1 = answer(21L, "A", true, 1, null, question);
        TestAnswer correct2 = answer(22L, "B", true, 2, null, question);
        TestAnswer wrong = answer(23L, "C", false, 3, null, question);

        TestSessionAnswer sessionAnswer = new TestSessionAnswer();
        sessionAnswer.getSelectedAnswers().add(correct1);
        sessionAnswer.getSelectedAnswers().add(wrong);

        assertEquals(0.0, scoringService.calculateAnswerRatio(sessionAnswer, question, List.of(correct1, correct2, wrong)));
    }

    @Test
    void shouldScoreMatchingByCorrectPairsShare() {
        TestQuestion question = question(3L, QuestionType.MATCHING, 2);
        TestAnswer left1 = answer(31L, "VP", false, 1, "Клапан", question);
        TestAnswer left2 = answer(32L, "PT", false, 2, "Датчик", question);

        TestSessionAnswer sessionAnswer = new TestSessionAnswer();
        sessionAnswer.setAnswerText("31=Клапан|||32=Ошибочно");

        assertEquals(0.5, scoringService.calculateAnswerRatio(sessionAnswer, question, List.of(left1, left2)));
    }

    @Test
    void shouldScoreSequenceByCorrectPositionsShare() {
        TestQuestion question = question(4L, QuestionType.SEQUENCE, 3);
        TestAnswer first = answer(41L, "Шаг 1", false, 1, null, question);
        TestAnswer second = answer(42L, "Шаг 2", false, 2, null, question);
        TestAnswer third = answer(43L, "Шаг 3", false, 3, null, question);

        TestSessionAnswer sessionAnswer = new TestSessionAnswer();
        sessionAnswer.setAnswerText("41,43,42");

        assertEquals(1.0 / 3.0, scoringService.calculateAnswerRatio(sessionAnswer, question, List.of(first, second, third)));
    }

    @Test
    void shouldScoreOpenTextIgnoringCaseAndTrim() {
        TestQuestion question = question(5L, QuestionType.OPEN_TEXT, 1);
        TestAnswer correct = answer(51L, "Азот", true, 1, null, question);

        TestSessionAnswer sessionAnswer = new TestSessionAnswer();
        sessionAnswer.setAnswerText("  азот ");

        assertEquals(1.0, scoringService.calculateAnswerRatio(sessionAnswer, question, List.of(correct)));
    }

    @Test
    void shouldCalculateWeightedPercentUsingDifficulty() {
        TestQuestion firstQuestion = question(101L, QuestionType.SINGLE_CHOICE, 1);
        TestQuestion secondQuestion = question(102L, QuestionType.SINGLE_CHOICE, 3);

        TestSessionAnswer firstAnswer = new TestSessionAnswer();
        firstAnswer.setTestQuestion(firstQuestion);
        firstAnswer.setEarnedScoreRatio(1.0);

        TestSessionAnswer secondAnswer = new TestSessionAnswer();
        secondAnswer.setTestQuestion(secondQuestion);
        secondAnswer.setEarnedScoreRatio(0.5);

        double result = scoringService.calculateWeightedPercent(
                List.of(firstAnswer, secondAnswer),
                List.of(firstQuestion, secondQuestion)
        );

        assertEquals(62.5, result);
    }

    private TestQuestion question(Long id, QuestionType type, int difficulty) {
        TestQuestion question = new TestQuestion();
        question.setId(id);
        question.setQuestionType(type);
        question.setDifficultyLevel(difficulty);
        return question;
    }

    private TestAnswer answer(Long id, String text, boolean correct, int sortOrder, String matchTarget, TestQuestion question) {
        TestAnswer answer = new TestAnswer();
        answer.setId(id);
        answer.setAnswerText(text);
        answer.setCorrect(correct);
        answer.setSortOrder(sortOrder);
        answer.setMatchTarget(matchTarget);
        answer.setQuestion(question);
        return answer;
    }
}
