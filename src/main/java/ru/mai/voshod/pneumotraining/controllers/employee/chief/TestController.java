package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestQuestionService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestService;

import java.util.Optional;

@Controller
@Slf4j
public class TestController {

    private final TestService testService;
    private final TestQuestionService testQuestionService;

    public TestController(TestService testService, TestQuestionService testQuestionService) {
        this.testService = testService;
        this.testQuestionService = testQuestionService;
    }

    // ========== Список тестов ==========

    @GetMapping("/employee/chief/tests/allTests")
    public String allTests(Model model) {
        model.addAttribute("allTests", testService.getAllTests());
        return "employee/chief/tests/allTests";
    }

    // ========== Создание теста ==========

    @GetMapping("/employee/chief/tests/addTest")
    public String addTestForm() {
        return "employee/chief/tests/addTest";
    }

    @PostMapping("/employee/chief/tests/addTest")
    public String addTest(@RequestParam String inputTitle,
                          @RequestParam(required = false) String inputDescription,
                          @RequestParam Integer inputTimeLimit,
                          @RequestParam Integer inputPassingScore,
                          @RequestParam(required = false, defaultValue = "false") boolean inputIsExam,
                          @RequestParam(required = false, defaultValue = "false") boolean inputAllowBackNavigation,
                          @AuthenticationPrincipal Employee currentUser,
                          Model model) {
        Optional<Long> result = testService.saveTest(inputTitle, inputDescription,
                inputTimeLimit, inputPassingScore, inputIsExam, inputAllowBackNavigation, currentUser);
        if (result.isEmpty()) {
            model.addAttribute("testError", "Ошибка при сохранении. Возможно, название уже занято.");
            return "employee/chief/tests/addTest";
        }
        return "redirect:/employee/chief/tests/detailsTest/" + result.get();
    }

    // ========== Детали теста ==========

    @GetMapping("/employee/chief/tests/detailsTest/{id}")
    public String detailsTest(@PathVariable(value = "id") long id, Model model) {
        Optional<TestDTO> testOptional = testService.getTestById(id);
        if (testOptional.isEmpty()) {
            return "redirect:/employee/chief/tests/allTests";
        }
        model.addAttribute("testDTO", testOptional.get());
        model.addAttribute("allQuestions", testQuestionService.getQuestionsByTest(id));
        return "employee/chief/tests/detailsTest";
    }

    // ========== Редактирование теста ==========

    @GetMapping("/employee/chief/tests/editTest/{id}")
    public String editTestForm(@PathVariable(value = "id") long id, Model model) {
        Optional<TestDTO> testOptional = testService.getTestById(id);
        if (testOptional.isEmpty()) {
            return "redirect:/employee/chief/tests/allTests";
        }
        model.addAttribute("testDTO", testOptional.get());
        return "employee/chief/tests/editTest";
    }

    @PostMapping("/employee/chief/tests/editTest/{id}")
    public String editTest(@PathVariable(value = "id") long id,
                           @RequestParam String inputTitle,
                           @RequestParam(required = false) String inputDescription,
                           @RequestParam Integer inputTimeLimit,
                           @RequestParam Integer inputPassingScore,
                           @RequestParam(required = false, defaultValue = "false") boolean inputIsExam,
                           @RequestParam(required = false, defaultValue = "false") boolean inputAllowBackNavigation,
                           RedirectAttributes redirectAttributes) {
        Optional<Long> result = testService.editTest(id, inputTitle, inputDescription,
                inputTimeLimit, inputPassingScore, inputIsExam, inputAllowBackNavigation);
        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("testError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/chief/tests/editTest/" + id;
        }
        return "redirect:/employee/chief/tests/detailsTest/" + id;
    }

    // ========== Удаление теста ==========

    @GetMapping("/employee/chief/tests/deleteTest/{id}")
    public String deleteTest(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        if (testService.deleteTest(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Тест удалён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении теста.");
        }
        return "redirect:/employee/chief/tests/allTests";
    }

    // ========== Активация / деактивация ==========

    @GetMapping("/employee/chief/tests/activateTest/{id}")
    public String activateTest(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        if (testService.toggleActive(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Статус теста изменён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Невозможно активировать тест. Убедитесь, что в тесте есть хотя бы один вопрос.");
        }
        return "redirect:/employee/chief/tests/detailsTest/" + id;
    }

    // ========== AJAX проверка уникальности ==========

    @GetMapping("/employee/chief/tests/check-title")
    @ResponseBody
    public java.util.Map<String, Boolean> checkTitle(@RequestParam String title,
                                                      @RequestParam(required = false) Long id) {
        java.util.Map<String, Boolean> response = new java.util.HashMap<>();
        response.put("exists", testService.checkTitle(title, id));
        return response;
    }
}
