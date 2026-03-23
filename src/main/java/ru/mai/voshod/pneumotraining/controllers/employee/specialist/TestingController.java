package ru.mai.voshod.pneumotraining.controllers.employee.specialist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestAssignmentService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.LearningPathService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.TestingService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class TestingController {

    private final TestingService testingService;
    private final TestAssignmentService testAssignmentService;
    private final LearningPathService learningPathService;

    public TestingController(TestingService testingService,
                             TestAssignmentService testAssignmentService,
                             LearningPathService learningPathService) {
        this.testingService = testingService;
        this.testAssignmentService = testAssignmentService;
        this.learningPathService = learningPathService;
    }

    // ========== Список доступных тестов ==========

    @GetMapping("/employee/specialist/testing/availableTests")
    public String availableTests(@AuthenticationPrincipal Employee currentUser, Model model) {
        model.addAttribute("availableTests", testingService.getAvailableTests(currentUser));
        model.addAttribute("assignedTests", testAssignmentService.getAssignedTestsForEmployee(currentUser.getId()));
        model.addAttribute("failedTests", testAssignmentService.getFailedTestsForEmployee(currentUser.getId()));
        return "employee/specialist/testing/availableTests";
    }

    // ========== Подтверждение перед началом ==========

    @GetMapping("/employee/specialist/testing/startTest/{testId}")
    public String startTestForm(@PathVariable(value = "testId") long testId,
                                @AuthenticationPrincipal Employee currentUser, Model model) {
        Optional<TestDTO> testOpt = testingService.getTestForStart(testId, currentUser);
        if (testOpt.isEmpty()) {
            return "redirect:/employee/specialist/testing/availableTests";
        }
        model.addAttribute("testDTO", testOpt.get());
        return "employee/specialist/testing/startTest";
    }

    @PostMapping("/employee/specialist/testing/startTest/{testId}")
    public String startTest(@PathVariable(value = "testId") long testId,
                            @AuthenticationPrincipal Employee currentUser,
                            RedirectAttributes redirectAttributes) {
        Optional<Long> sessionId = testingService.startTest(testId, currentUser);
        if (sessionId.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось начать тест.");
            return "redirect:/employee/specialist/testing/availableTests";
        }
        return "redirect:/employee/specialist/testing/question/" + sessionId.get() + "/0";
    }

    // ========== Отображение вопроса ==========

    @GetMapping("/employee/specialist/testing/question/{sessionId}/{questionIndex}")
    public String question(@PathVariable(value = "sessionId") long sessionId,
                           @PathVariable(value = "questionIndex") int questionIndex,
                           @AuthenticationPrincipal Employee currentUser,
                           Model model) {
        // Проверка: не истекло ли время
        if (testingService.isSessionExpired(sessionId, currentUser)) {
            testingService.finishTest(sessionId, currentUser, TestSessionStatus.EXPIRED);
            return "redirect:/employee/specialist/testing/result/" + sessionId;
        }

        Optional<Map<String, Object>> questionData = testingService.getQuestionForDisplay(
                sessionId, questionIndex, currentUser);

        if (questionData.isEmpty()) {
            return "redirect:/employee/specialist/testing/availableTests";
        }

        questionData.get().forEach(model::addAttribute);
        return "employee/specialist/testing/question";
    }

    // ========== Сохранение ответа ==========

    @PostMapping("/employee/specialist/testing/submitAnswer/{sessionId}/{questionIndex}")
    public String submitAnswer(@PathVariable(value = "sessionId") long sessionId,
                               @PathVariable(value = "questionIndex") int questionIndex,
                               @AuthenticationPrincipal Employee currentUser,
                               HttpServletRequest request) {
        Optional<Integer> result = testingService.submitAnswer(
                sessionId, questionIndex, currentUser, request.getParameterMap());

        if (result.isEmpty()) {
            return "redirect:/employee/specialist/testing/availableTests";
        }

        int nextIndex = result.get();
        if (nextIndex == -1 || nextIndex == -2) {
            // -1: время истекло, -2: последний вопрос
            return "redirect:/employee/specialist/testing/finishTest/" + sessionId;
        }

        return "redirect:/employee/specialist/testing/question/" + sessionId + "/" + nextIndex;
    }

    // ========== Завершение теста ==========

    @GetMapping("/employee/specialist/testing/finishTest/{sessionId}")
    public String finishTest(@PathVariable(value = "sessionId") long sessionId,
                             @AuthenticationPrincipal Employee currentUser) {
        testingService.finishTest(sessionId, currentUser, TestSessionStatus.COMPLETED);
        return "redirect:/employee/specialist/testing/result/" + sessionId;
    }

    // ========== Результат ==========

    @GetMapping("/employee/specialist/testing/result/{sessionId}")
    public String result(@PathVariable(value = "sessionId") long sessionId,
                         @AuthenticationPrincipal Employee currentUser,
                         Model model) {
        Optional<TestSessionDTO> sessionOpt = testingService.getSessionResult(sessionId, currentUser);
        if (sessionOpt.isEmpty()) {
            return "redirect:/employee/specialist/testing/availableTests";
        }
        model.addAttribute("sessionDTO", sessionOpt.get());
        model.addAttribute("answerDetails", testingService.getSessionAnswerDetails(sessionId));
        model.addAttribute("recommendations", learningPathService.getRecommendations(sessionId, currentUser));
        return "employee/specialist/testing/result";
    }
}
