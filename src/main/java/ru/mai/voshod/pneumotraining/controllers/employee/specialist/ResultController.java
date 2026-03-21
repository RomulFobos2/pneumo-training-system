package ru.mai.voshod.pneumotraining.controllers.employee.specialist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.mai.voshod.pneumotraining.dto.TestSessionAnswerDTO;
import ru.mai.voshod.pneumotraining.dto.TestSessionDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.specialist.ResultService;
import ru.mai.voshod.pneumotraining.service.employee.specialist.SimulationService;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee/specialist/results")
@Slf4j
public class ResultController {

    private final ResultService resultService;
    private final SimulationService simulationService;

    public ResultController(ResultService resultService, SimulationService simulationService) {
        this.resultService = resultService;
        this.simulationService = simulationService;
    }

    @GetMapping("/myResults")
    public String myResults(Model model, @AuthenticationPrincipal Employee currentUser) {
        model.addAttribute("sessions", resultService.getMyResults(currentUser));
        model.addAttribute("simSessions", simulationService.getMyResults(currentUser));
        return "employee/specialist/results/myResults";
    }

    @GetMapping("/detailsResult/{sessionId}")
    public String detailsResult(@PathVariable Long sessionId,
                                @AuthenticationPrincipal Employee currentUser,
                                Model model) {
        Optional<TestSessionDTO> sessionOpt = resultService.getSessionResult(sessionId, currentUser);
        if (sessionOpt.isEmpty()) {
            return "redirect:/employee/specialist/results/myResults";
        }

        List<TestSessionAnswerDTO> answerDetails = resultService.getSessionAnswerDetails(sessionId, currentUser);

        model.addAttribute("sessionDTO", sessionOpt.get());
        model.addAttribute("answerDetails", answerDetails);
        return "employee/specialist/results/detailsResult";
    }

    @GetMapping("/exportResult/{sessionId}")
    public ResponseEntity<byte[]> exportResult(@PathVariable Long sessionId,
                                               @AuthenticationPrincipal Employee currentUser) {
        byte[] excelData = resultService.exportSessionToExcel(sessionId, currentUser);
        if (excelData == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=result_" + sessionId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }
}
