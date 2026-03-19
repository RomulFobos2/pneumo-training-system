package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.TestAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.dto.TestQuestionDTO;
import ru.mai.voshod.pneumotraining.enumeration.QuestionType;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestQuestionService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestService;

import java.util.*;

@Controller
@Slf4j
public class TestQuestionController {

    private final TestQuestionService testQuestionService;
    private final TestService testService;

    public TestQuestionController(TestQuestionService testQuestionService,
                                   TestService testService) {
        this.testQuestionService = testQuestionService;
        this.testService = testService;
    }

    // ========== Добавление вопроса ==========

    @GetMapping("/employee/chief/tests/addQuestion/{testId}")
    public String addQuestionForm(@PathVariable(value = "testId") long testId, Model model) {
        Optional<TestDTO> testOptional = testService.getTestById(testId);
        if (testOptional.isEmpty()) {
            return "redirect:/employee/chief/tests/allTests";
        }
        model.addAttribute("testDTO", testOptional.get());
        model.addAttribute("allQuestionTypes", QuestionType.values());
        return "employee/chief/tests/addQuestion";
    }

    @PostMapping("/employee/chief/tests/addQuestion/{testId}")
    public String addQuestion(@PathVariable(value = "testId") long testId,
                              @RequestParam String inputQuestionText,
                              @RequestParam Integer inputSortOrder,
                              @RequestParam String inputQuestionType,
                              @RequestParam(required = false) List<String> answerText,
                              @RequestParam(required = false) List<String> answerCorrect,
                              @RequestParam(required = false) List<String> answerSortOrder,
                              @RequestParam(required = false) List<String> answerMatchTarget,
                              Model model) {

        List<TestAnswerDTO> answerDTOs = buildAnswerDTOs(inputQuestionType, answerText,
                answerCorrect, answerSortOrder, answerMatchTarget);

        Optional<Long> result = testQuestionService.saveQuestion(testId, inputQuestionText,
                inputSortOrder, inputQuestionType, answerDTOs);

        if (result.isEmpty()) {
            model.addAttribute("questionError", "Ошибка при сохранении. Проверьте варианты ответа и правильные ответы.");
            model.addAttribute("testDTO", testService.getTestById(testId).orElse(null));
            model.addAttribute("allQuestionTypes", QuestionType.values());
            return "employee/chief/tests/addQuestion";
        }
        return "redirect:/employee/chief/tests/detailsTest/" + testId;
    }

    // ========== Редактирование вопроса ==========

    @GetMapping("/employee/chief/tests/editQuestion/{questionId}")
    public String editQuestionForm(@PathVariable(value = "questionId") long questionId, Model model) {
        Optional<TestQuestionDTO> questionOptional = testQuestionService.getQuestionById(questionId);
        if (questionOptional.isEmpty()) {
            return "redirect:/employee/chief/tests/allTests";
        }
        model.addAttribute("questionDTO", questionOptional.get());
        model.addAttribute("allQuestionTypes", QuestionType.values());
        return "employee/chief/tests/editQuestion";
    }

    @PostMapping("/employee/chief/tests/editQuestion/{questionId}")
    public String editQuestion(@PathVariable(value = "questionId") long questionId,
                               @RequestParam String inputQuestionText,
                               @RequestParam Integer inputSortOrder,
                               @RequestParam String inputQuestionType,
                               @RequestParam(required = false) List<String> answerText,
                               @RequestParam(required = false) List<String> answerCorrect,
                               @RequestParam(required = false) List<String> answerSortOrder,
                               @RequestParam(required = false) List<String> answerMatchTarget,
                               RedirectAttributes redirectAttributes) {

        Optional<Long> testIdOpt = testQuestionService.getTestIdByQuestionId(questionId);
        List<TestAnswerDTO> answerDTOs = buildAnswerDTOs(inputQuestionType, answerText,
                answerCorrect, answerSortOrder, answerMatchTarget);

        Optional<Long> result = testQuestionService.editQuestion(questionId, inputQuestionText,
                inputSortOrder, inputQuestionType, answerDTOs);

        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("questionError", "Ошибка при сохранении. Проверьте варианты ответа и правильные ответы.");
            return "redirect:/employee/chief/tests/editQuestion/" + questionId;
        }
        return "redirect:/employee/chief/tests/detailsTest/" + testIdOpt.orElse(0L);
    }

    // ========== Удаление вопроса ==========

    @GetMapping("/employee/chief/tests/deleteQuestion/{questionId}")
    public String deleteQuestion(@PathVariable(value = "questionId") long questionId,
                                  RedirectAttributes redirectAttributes) {
        Optional<Long> testIdOpt = testQuestionService.getTestIdByQuestionId(questionId);
        long testId = testIdOpt.orElse(0L);

        if (testQuestionService.deleteQuestion(questionId)) {
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос удалён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении вопроса.");
        }
        return "redirect:/employee/chief/tests/detailsTest/" + testId;
    }

    // ========== Перестановка (AJAX) ==========

    @PostMapping("/employee/chief/tests/reorderQuestions/{testId}")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> reorderQuestions(
            @PathVariable(value = "testId") long testId,
            @RequestBody List<Long> orderedIds) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", testQuestionService.reorderQuestions(testId, orderedIds));
        return ResponseEntity.ok(response);
    }

    // ========== Вспомогательный метод ==========

    /**
     * Собирает список TestAnswerDTO из параметров формы.
     * Каждый тип вопроса передаёт ответы по-своему:
     * - SINGLE/MULTIPLE: answerText[] + answerCorrect[] (индексы отмеченных)
     * - SEQUENCE: answerText[] + answerSortOrder[]
     * - MATCHING: answerText[] (левый) + answerMatchTarget[] (правый)
     * - OPEN_TEXT: answerText[0] — эталонный ответ
     */
    private List<TestAnswerDTO> buildAnswerDTOs(String questionType,
                                                 List<String> answerText,
                                                 List<String> answerCorrect,
                                                 List<String> answerSortOrder,
                                                 List<String> answerMatchTarget) {
        List<TestAnswerDTO> result = new ArrayList<>();
        if (answerText == null || answerText.isEmpty()) return result;

        // Множество индексов правильных ответов (для SINGLE/MULTIPLE)
        Set<String> correctIndices = new HashSet<>();
        if (answerCorrect != null) {
            correctIndices.addAll(answerCorrect);
        }

        for (int i = 0; i < answerText.size(); i++) {
            String text = answerText.get(i);
            if (text == null || text.isBlank()) continue;

            TestAnswerDTO dto = new TestAnswerDTO();
            dto.setAnswerText(text.trim());
            dto.setSortOrder(i + 1);

            switch (questionType) {
                case "SINGLE_CHOICE":
                case "MULTIPLE_CHOICE":
                    dto.setCorrect(correctIndices.contains(String.valueOf(i)));
                    break;
                case "SEQUENCE":
                    if (answerSortOrder != null && i < answerSortOrder.size()) {
                        try {
                            dto.setSortOrder(Integer.parseInt(answerSortOrder.get(i)));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case "MATCHING":
                    if (answerMatchTarget != null && i < answerMatchTarget.size()) {
                        dto.setMatchTarget(answerMatchTarget.get(i));
                    }
                    break;
                case "OPEN_TEXT":
                    dto.setCorrect(true);
                    break;
            }

            result.add(dto);
        }

        return result;
    }
}
