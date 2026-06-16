package ru.mai.voshod.pneumotraining.controllers.employee.chief;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.TestDTO;
import ru.mai.voshod.pneumotraining.enumeration.TestSessionStatus;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestPreviewService;
import ru.mai.voshod.pneumotraining.service.employee.chief.TestService;
import ru.mai.voshod.pneumotraining.service.employee.chief.preview.TestPreviewState;

import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/employee/chief/tests/preview")
@Slf4j
public class TestPreviewController {

    private static final String SESSION_ATTR = "chiefTestPreview";

    private final TestPreviewService previewService;
    private final TestService testService;

    public TestPreviewController(TestPreviewService previewService, TestService testService) {
        this.previewService = previewService;
        this.testService = testService;
    }

    // ========== Стартовая страница ==========

    @GetMapping("/{testId}")
    public String startForm(@PathVariable(value = "testId") long testId, Model model) {
        Optional<TestDTO> testOpt = testService.getTestById(testId);
        if (testOpt.isEmpty()) {
            return "redirect:/employee/chief/tests/allTests";
        }
        model.addAttribute("testDTO", testOpt.get());
        return "employee/chief/tests/preview/start";
    }

    @PostMapping("/{testId}/start")
    public String start(@PathVariable(value = "testId") long testId,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<TestPreviewState> stateOpt = previewService.startPreview(testId);
        if (stateOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось начать пробный прогон: недостаточно вопросов или тест недоступен.");
            return "redirect:/employee/chief/tests/detailsTest/" + testId;
        }
        session.setAttribute(SESSION_ATTR, stateOpt.get());
        return "redirect:/employee/chief/tests/preview/question/0";
    }

    // ========== Вопрос ==========

    @GetMapping("/question/{idx}")
    public String question(@PathVariable(value = "idx") int idx,
                           HttpSession session, Model model) {
        TestPreviewState state = (TestPreviewState) session.getAttribute(SESSION_ATTR);
        if (state == null) {
            return "redirect:/employee/chief/tests/allTests";
        }
        if (previewService.isExpired(state)) {
            previewService.finishPreview(state, TestSessionStatus.EXPIRED);
            return "redirect:/employee/chief/tests/preview/result";
        }
        if (state.isFinished()) {
            return "redirect:/employee/chief/tests/preview/result";
        }
        Optional<Map<String, Object>> data = previewService.getQuestionForDisplay(state, idx);
        if (data.isEmpty()) {
            return "redirect:/employee/chief/tests/preview/result";
        }
        data.get().forEach(model::addAttribute);
        model.addAttribute("testId", state.getTestId());
        return "employee/chief/tests/preview/question";
    }

    @PostMapping("/answer/{idx}")
    public String answer(@PathVariable(value = "idx") int idx,
                         HttpSession session, HttpServletRequest request) {
        TestPreviewState state = (TestPreviewState) session.getAttribute(SESSION_ATTR);
        if (state == null) {
            return "redirect:/employee/chief/tests/allTests";
        }
        Optional<Integer> r = previewService.submitAnswer(state, idx, request.getParameterMap());
        if (r.isEmpty()) {
            return "redirect:/employee/chief/tests/preview/result";
        }
        int next = r.get();
        if (next == -1 || next == -2) {
            return "redirect:/employee/chief/tests/preview/finish";
        }
        return "redirect:/employee/chief/tests/preview/question/" + next;
    }

    // ========== Завершение и результат ==========

    @GetMapping("/finish")
    public String finish(HttpSession session) {
        TestPreviewState state = (TestPreviewState) session.getAttribute(SESSION_ATTR);
        if (state == null) {
            return "redirect:/employee/chief/tests/allTests";
        }
        if (!state.isFinished()) {
            TestSessionStatus status = previewService.isExpired(state)
                    ? TestSessionStatus.EXPIRED
                    : TestSessionStatus.COMPLETED;
            previewService.finishPreview(state, status);
        }
        return "redirect:/employee/chief/tests/preview/result";
    }

    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        TestPreviewState state = (TestPreviewState) session.getAttribute(SESSION_ATTR);
        if (state == null) {
            return "redirect:/employee/chief/tests/allTests";
        }
        if (!state.isFinished()) {
            return "redirect:/employee/chief/tests/preview/question/0";
        }
        model.addAttribute("sessionDTO", previewService.buildSessionDTO(state));
        model.addAttribute("answerDetails", previewService.buildResultDetails(state));
        model.addAttribute("recommendations", previewService.buildRecommendations(state));
        model.addAttribute("testId", state.getTestId());
        return "employee/chief/tests/preview/result";
    }

    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        TestPreviewState state = (TestPreviewState) session.getAttribute(SESSION_ATTR);
        Long testId = state != null ? state.getTestId() : null;
        session.removeAttribute(SESSION_ATTR);
        if (testId != null) {
            return "redirect:/employee/chief/tests/detailsTest/" + testId;
        }
        return "redirect:/employee/chief/tests/allTests";
    }
}
